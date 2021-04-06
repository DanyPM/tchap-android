use std::{
    collections::{BTreeMap, HashMap},
    convert::{TryFrom, TryInto},
    io::Cursor,
};

use serde_json::{json, value::RawValue};
use tokio::runtime::Runtime;

use matrix_sdk_common::{
    api::r0::{
        keys::{
            claim_keys::Response as KeysClaimResponse, get_keys::Response as KeysQueryResponse,
            upload_keys::Response as KeysUploadResponse,
        },
        sync::sync_events::{DeviceLists as RumaDeviceLists, ToDevice},
        to_device::send_event_to_device::Response as ToDeviceResponse,
    },
    deserialized_responses::events::{AlgorithmInfo, SyncMessageEvent},
    events::{room::encrypted::EncryptedEventContent, AnyMessageEventContent, EventContent},
    identifiers::{DeviceKeyAlgorithm, RoomId, UserId},
    uuid::Uuid,
    UInt,
};

use matrix_sdk_crypto::{
    decrypt_key_export, encrypt_key_export, Device as InnerDevice, EncryptionSettings,
    OlmMachine as InnerMachine,
};

use crate::{
    error::{CryptoStoreError, DecryptionError, MachineCreationError},
    responses::{response_from_string, OwnedResponse},
    DecryptedEvent, DeviceLists, KeyImportError, KeysImportResult, ProgressListener, Request,
    RequestType,
};

/// A high level state machine that handles E2EE for Matrix.
pub struct OlmMachine {
    inner: InnerMachine,
    runtime: Runtime,
}

pub struct Device {
    pub user_id: String,
    pub device_id: String,
    pub keys: HashMap<String, String>,
}

impl From<InnerDevice> for Device {
    fn from(d: InnerDevice) -> Self {
        Device {
            user_id: d.user_id().to_string(),
            device_id: d.device_id().to_string(),
            keys: d
                .keys()
                .iter()
                .map(|(k, v)| (k.to_string(), v.to_string()))
                .collect(),
        }
    }
}

pub struct Sas {
    pub other_user_id: String,
    pub other_device_id: String,
    pub flow_id: String,
    pub request: Request,
}

impl OlmMachine {
    pub fn new(user_id: &str, device_id: &str, path: &str) -> Result<Self, MachineCreationError> {
        let user_id = UserId::try_from(user_id)?;
        let device_id = device_id.into();
        let runtime = Runtime::new().unwrap();

        Ok(OlmMachine {
            inner: runtime.block_on(InnerMachine::new_with_default_store(
                &user_id, device_id, path, None,
            ))?,
            runtime,
        })
    }

    pub fn user_id(&self) -> String {
        self.inner.user_id().to_string()
    }

    pub fn device_id(&self) -> String {
        self.inner.device_id().to_string()
    }

    pub fn get_device(&self, user_id: &str, device_id: &str) -> Option<Device> {
        let user_id = UserId::try_from(user_id).unwrap();

        self.runtime
            .block_on(self.inner.get_device(&user_id, device_id.into()))
            .unwrap()
            .map(|d| d.into())
    }

    pub fn get_user_devices(&self, user_id: &str) -> Vec<Device> {
        let user_id = UserId::try_from(user_id).unwrap();
        self.runtime
            .block_on(self.inner.get_user_devices(&user_id))
            .unwrap()
            .devices()
            .map(|d| d.into())
            .collect()
    }

    pub fn identity_keys(&self) -> HashMap<String, String> {
        self.inner
            .identity_keys()
            .iter()
            .map(|(k, v)| (k.to_owned(), v.to_owned()))
            .collect()
    }

    pub fn mark_request_as_sent(
        &self,
        request_id: &str,
        request_type: RequestType,
        response_body: &str,
    ) -> Result<(), CryptoStoreError> {
        let id = Uuid::parse_str(request_id).expect("Can't parse request id");

        let response = response_from_string(&response_body);

        let response: OwnedResponse = match request_type {
            RequestType::KeysUpload => KeysUploadResponse::try_from(response).map(Into::into),
            RequestType::KeysQuery => KeysQueryResponse::try_from(response).map(Into::into),
            RequestType::ToDevice => ToDeviceResponse::try_from(response).map(Into::into),
            RequestType::KeysClaim => KeysClaimResponse::try_from(response).map(Into::into),
        }
        .expect("Can't convert json string to response");

        self.runtime
            .block_on(self.inner.mark_request_as_sent(&id, &response))?;

        Ok(())
    }

    pub fn start_verification(&self, device: &Device) -> Result<Sas, CryptoStoreError> {
        let user_id = UserId::try_from(device.user_id.clone()).unwrap();
        let device_id = device.device_id.as_str().into();
        let device = self
            .runtime
            .block_on(self.inner.get_device(&user_id, device_id))?
            .unwrap();

        let (sas, request) = self.runtime.block_on(device.start_verification())?;

        Ok(Sas {
            other_user_id: sas.other_user_id().to_string(),
            other_device_id: sas.other_device_id().to_string(),
            flow_id: sas.flow_id().as_str().to_owned(),
            request: request.into(),
        })
    }

    pub fn outgoing_requests(&self) -> Vec<Request> {
        self.runtime
            .block_on(self.inner.outgoing_requests())
            .into_iter()
            .map(|r| r.into())
            .collect()
    }

    pub fn receive_sync_changes(
        &self,
        events: &str,
        device_changes: DeviceLists,
        key_counts: HashMap<String, i32>,
    ) {
        let events: ToDevice = serde_json::from_str(events).unwrap();
        let device_changes: RumaDeviceLists = device_changes.into();
        let key_counts: BTreeMap<DeviceKeyAlgorithm, UInt> = key_counts
            .into_iter()
            .map(|(k, v)| {
                (
                    DeviceKeyAlgorithm::from(k),
                    v.clamp(0, i32::MAX)
                        .try_into()
                        .expect("Couldn't convert key counts into an UInt"),
                )
            })
            .collect();

        self.runtime
            .block_on(
                self.inner
                    .receive_sync_changes(&events, &device_changes, &key_counts),
            )
            .unwrap();
    }

    pub fn update_tracked_users(&self, users: Vec<String>) {
        let users: Vec<UserId> = users
            .into_iter()
            .filter_map(|u| UserId::try_from(u).ok())
            .collect();

        self.runtime
            .block_on(self.inner.update_tracked_users(users.iter()));
    }

    pub fn share_group_session(&self, room_id: &str, users: Vec<String>) -> Vec<Request> {
        let users: Vec<UserId> = users
            .into_iter()
            .filter_map(|u| UserId::try_from(u).ok())
            .collect();

        let room_id = RoomId::try_from(room_id).unwrap();
        let requests = self
            .runtime
            .block_on(self.inner.share_group_session(
                &room_id,
                users.iter(),
                EncryptionSettings::default(),
            ))
            .unwrap();

        requests.into_iter().map(|r| (&*r).into()).collect()
    }

    pub fn get_missing_sessions(
        &self,
        users: Vec<String>,
    ) -> Result<Option<Request>, CryptoStoreError> {
        let users: Vec<UserId> = users
            .into_iter()
            .filter_map(|u| UserId::try_from(u).ok())
            .collect();

        Ok(self
            .runtime
            .block_on(self.inner.get_missing_sessions(users.iter()))?
            .map(|(request_id, request)| Request::KeysClaim {
                request_id: request_id.to_string(),
                one_time_keys: request
                    .one_time_keys
                    .into_iter()
                    .map(|(u, d)| {
                        (
                            u.to_string(),
                            d.into_iter()
                                .map(|(k, v)| (k.to_string(), v.to_string()))
                                .collect(),
                        )
                    })
                    .collect(),
            }))
    }

    pub fn encrypt(&self, room_id: &str, event_type: &str, content: &str) -> String {
        let room_id = RoomId::try_from(room_id).unwrap();
        let content: Box<RawValue> = serde_json::from_str(content).unwrap();

        let content = AnyMessageEventContent::from_parts(event_type, content).unwrap();
        let encrypted_content = self
            .runtime
            .block_on(self.inner.encrypt(&room_id, content))
            .unwrap();

        serde_json::to_string(&encrypted_content).unwrap()
    }

    pub fn export_keys(&self, passphrase: &str, rounds: i32) -> Result<String, CryptoStoreError> {
        let keys = self.runtime.block_on(self.inner.export_keys(|_| true))?;

        let encrypted = encrypt_key_export(&keys, passphrase, rounds as u32)
            .map_err(CryptoStoreError::Serialization)?;

        Ok(encrypted)
    }

    pub fn import_keys(
        &self,
        keys: &str,
        passphrase: &str,
        _: Box<dyn ProgressListener>,
    ) -> Result<KeysImportResult, KeyImportError> {
        let keys = Cursor::new(keys);
        let keys = decrypt_key_export(keys, passphrase)?;

        // TODO use the progress listener
        let result = self.runtime.block_on(self.inner.import_keys(keys))?;

        Ok(KeysImportResult {
            total: result.1 as i32,
            imported: result.0 as i32,
        })
    }

    pub fn decrypt_room_event(
        &self,
        event: &str,
        room_id: &str,
    ) -> Result<DecryptedEvent, DecryptionError> {
        let event: SyncMessageEvent<EncryptedEventContent> = serde_json::from_str(event)?;
        let room_id = RoomId::try_from(room_id)?;

        let decrypted = self
            .runtime
            .block_on(self.inner.decrypt_room_event(&event, &room_id))?;

        let encryption_info = decrypted
            .encryption_info()
            .expect("Decrypted event didn't contain any encryption info");

        let content = decrypted.content();

        let clear_event = json!({
            "type": content.event_type(),
            "content": content,
        });

        Ok(match &encryption_info.algorithm_info {
            AlgorithmInfo::MegolmV1AesSha2 {
                curve25519_key,
                sender_claimed_keys,
                forwarding_curve25519_key_chain,
            } => DecryptedEvent {
                clear_event: serde_json::to_string(&clear_event)?,
                sender_curve25519_key: curve25519_key.to_owned(),
                claimed_ed25519_key: sender_claimed_keys
                    .get(&DeviceKeyAlgorithm::Ed25519)
                    .cloned(),
                forwarding_curve25519_chain: forwarding_curve25519_key_chain.to_owned(),
            },
        })
    }
}
