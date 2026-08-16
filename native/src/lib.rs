use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::Deserialize;
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

#[derive(Debug, Deserialize)]
struct RealityProfile {
    #[serde(rename = "serverAddress")]
    server_address: String,
    #[serde(rename = "serverPort")]
    server_port: u16,
    security: String,
    network: String,
    sni: Option<String>,
    #[serde(rename = "publicKeyPresent")]
    public_key_present: bool,
    #[serde(rename = "shortIdPresent")]
    short_id_present: bool,
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

#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    json_string(&mut env, "shadevpn-native/0.2.1")
}

#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeBuildLane(
    mut env: JNIEnv,
    _class: JClass,
    profile_json: JString,
) -> jstring {
    let result = read_profile(&mut env, profile_json).and_then(|profile| {
        if !profile.security.eq_ignore_ascii_case("reality") {
            return Err("only Reality profiles are accepted".to_owned());
        }
        if !matches!(profile.network.as_str(), "tcp" | "ws" | "xhttp") {
            return Err("unsupported Reality network".to_owned());
        }
        if profile.sni.as_deref().unwrap_or_default().is_empty() {
            return Err("Reality SNI is required".to_owned());
        }
        if !profile.public_key_present || !profile.short_id_present {
            return Err("Reality public key and short ID are required".to_owned());
        }
        Ok(format!(
            "{{\"lane\":\"vless-reality\",\"transport\":\"tcp\",\"host\":\"{}\",\"port\":{},\"status\":\"validated\"}}",
            profile.server_address, profile.server_port
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
    match read_profile(&mut env, profile_json) {
        Ok(profile) => json_string(
            &mut env,
            &format!(
                "{{\"valid\":{},\"reason\":\"{}\"}}",
                profile.security.eq_ignore_ascii_case("reality")
                    && profile.public_key_present
                    && profile.short_id_present
                    && profile
                        .sni
                        .as_deref()
                        .is_some_and(|value| !value.is_empty()),
                if profile.security.eq_ignore_ascii_case("reality") {
                    "Reality profile parsed"
                } else {
                    "security must be Reality"
                }
            ),
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
/// reported as Connected and is not the VLESS/Reality handshake. The actual
/// Reality TLS and VLESS framing implementation remains the next native step.
#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeProbeControlPlane(
    mut env: JNIEnv,
    _class: JClass,
    profile_json: JString,
) -> jstring {
    let result = read_profile(&mut env, profile_json).and_then(|profile| {
        let address = format!("{}:{}", profile.server_address, profile.server_port);
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
    fn parses_reality_profile_shape() {
        let profile: RealityProfile = serde_json::from_str(
            r#"{
                "serverAddress":"127.0.0.1",
                "serverPort":443,
                "security":"reality",
                "network":"tcp",
                "sni":"example.com",
                "publicKeyPresent":true,
                "shortIdPresent":true
            }"#,
        )
        .expect("profile should parse");
        assert_eq!(profile.server_port, 443);
        assert!(profile.public_key_present);
    }
}
