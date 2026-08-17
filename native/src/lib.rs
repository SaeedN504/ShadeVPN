mod reality;

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use reality::RealityConfig;
use serde::Deserialize;
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

#[derive(Debug, Deserialize)]
struct RealityProfile {
    #[serde(rename = "serverAddress")]
    server_address: String,
    #[serde(rename = "serverPort")]
    server_port: u16,
    uuid: Option<String>,
    security: String,
    network: String,
    sni: Option<String>,
    #[serde(rename = "publicKey")]
    public_key: Option<String>,
    #[serde(rename = "shortId")]
    short_id: Option<String>,
}

fn json_string(env: &mut JNIEnv, value: &str) -> jstring {
    env.new_string(value).expect("JNI string").into_raw()
}

fn json_error(reason: &str) -> String {
    format!(
        "{{\"reachable\":false,\"reason\":\"{}\"}}",
        reason.replace('"', "\\\"")
    )
}

fn read_profile(env: &mut JNIEnv, value: JString) -> Result<RealityProfile, String> {
    let raw = env
        .get_string(&value)
        .map_err(|_| "unable to read profile payload".to_owned())?
        .to_string_lossy()
        .into_owned();
    serde_json::from_str(&raw).map_err(|_| "invalid sanitized profile JSON".to_owned())
}

fn build_config(profile: RealityProfile) -> Result<RealityConfig, String> {
    let uuid = profile
        .uuid
        .as_deref()
        .ok_or_else(|| "VLESS UUID is required".to_owned())
        .and_then(RealityConfig::parse_uuid)?;
    let sni = profile
        .sni
        .filter(|value| !value.is_empty())
        .ok_or_else(|| "Reality SNI is required".to_owned())?;
    if !profile.security.eq_ignore_ascii_case("reality") {
        return Err("only Reality profiles are accepted".to_owned());
    }
    if !matches!(profile.network.as_str(), "tcp" | "ws" | "xhttp") {
        return Err("unsupported Reality network".to_owned());
    }
    let public_key = profile
        .public_key
        .filter(|value| !value.is_empty())
        .ok_or_else(|| "Reality public key is required".to_owned())?;
    let short_id = profile
        .short_id
        .filter(|value| !value.is_empty())
        .ok_or_else(|| "Reality short ID is required".to_owned())?;
    Ok(RealityConfig {
        server_address: profile.server_address,
        server_port: profile.server_port,
        uuid,
        sni,
        public_key,
        short_id,
        network: profile.network,
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    json_string(&mut env, "shadevpn-native/0.3.1")
}

#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeBuildLane(
    mut env: JNIEnv,
    _class: JClass,
    profile_json: JString,
) -> jstring {
    let result = read_profile(&mut env, profile_json)
        .and_then(build_config)
        .and_then(|config| {
            let request = config.build_vless_tcp_request()?;
            Ok(format!(
                "{{\"lane\":\"vless-reality\",\"transport\":\"tcp\",\"host\":\"{}\",\"port\":{},\"vlessRequestBytes\":{},\"status\":\"framing-ready\"}}",
                config.server_address,
                config.server_port,
                request.len()
            ))
        });
    match result {
        Ok(value) => json_string(&mut env, &value),
        Err(reason) => json_string(
            &mut env,
            &format!(
                "{{\"lane\":\"invalid\",\"status\":\"rejected\",\"reason\":\"{}\"}}",
                reason.replace('"', "\\\"")
            ),
        ),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeValidateProfile(
    mut env: JNIEnv,
    _class: JClass,
    profile_json: JString,
) -> jstring {
    match read_profile(&mut env, profile_json).and_then(build_config) {
        Ok(_) => json_string(
            &mut env,
            "{\"valid\":true,\"reason\":\"Reality profile parsed and VLESS framing validated\"}",
        ),
        Err(reason) => json_string(
            &mut env,
            &format!(
                "{{\"valid\":false,\"reason\":\"{}\"}}",
                reason.replace('"', "\\\"")
            ),
        ),
    }
}

/// Performs only a bounded TCP reachability check. This is deliberately not
/// reported as Connected and is not the VLESS/Reality handshake.
#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeProbeControlPlane(
    mut env: JNIEnv,
    _class: JClass,
    profile_json: JString,
) -> jstring {
    let result = read_profile(&mut env, profile_json)
        .and_then(build_config)
        .and_then(|config| {
            let address = format!("{}:{}", config.server_address, config.server_port);
            let socket = address
                .to_socket_addrs()
                .map_err(|_| "DNS resolution failed".to_owned())?
                .next()
                .ok_or_else(|| "no resolved address".to_owned())?;
            TcpStream::connect_timeout(&socket, Duration::from_secs(8))
                .map(|_| "TCP endpoint reachable".to_owned())
                .map_err(|error| format!("TCP connect failed: {error}"))
        });
    match result {
        Ok(reason) => json_string(
            &mut env,
            &format!(
                "{{\"reachable\":true,\"reason\":\"{}\"}}",
                reason.replace('"', "\\\"")
            ),
        ),
        Err(reason) => json_string(&mut env, &json_error(&reason)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_profile_without_uuid() {
        let profile: RealityProfile = serde_json::from_str(
            r#"{
            "serverAddress":"127.0.0.1","serverPort":443,"security":"reality",
            "network":"tcp","sni":"example.com","publicKey":"pbk","shortId":"sid"
        }"#,
        )
        .unwrap();
        assert!(build_config(profile).is_err());
    }

    #[test]
    fn rejects_profile_without_key_material() {
        let profile: RealityProfile = serde_json::from_str(
            r#"{
            "serverAddress":"127.0.0.1","serverPort":443,"security":"reality",
            "network":"tcp","sni":"example.com",
            "uuid":"00000000-0000-0000-0000-000000000001"
        }"#,
        )
        .unwrap();
        assert_eq!(
            build_config(profile).unwrap_err(),
            "Reality public key is required"
        );
    }

    #[test]
    fn accepts_complete_reality_profile() {
        let profile: RealityProfile = serde_json::from_str(
            r#"{
            "serverAddress":"127.0.0.1","serverPort":443,"security":"reality",
            "network":"tcp","sni":"example.com",
            "uuid":"00000000-0000-0000-0000-000000000001",
            "publicKey":"pbk","shortId":"sid"
        }"#,
        )
        .unwrap();
        let config = build_config(profile).expect("profile should build");
        assert_eq!(config.public_key, "pbk");
        assert_eq!(config.short_id, "sid");
    }
}
