use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

/// Minimal, intentionally boring JNI boundary for milestone 2.
/// No sockets yet, just validation and lane planning for Reality.
#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    env.new_string("shadevpn-native/0.2.0")
        .expect("JNI string")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeBuildLane(
    mut env: JNIEnv,
    _class: JClass,
    profile_json: JString,
) -> jstring {
    let profile = env
        .get_string(&profile_json)
        .expect("profile json")
        .to_string_lossy()
        .into_owned();

    let outcome = if profile.contains("\"security\":\"reality\"") {
        "{\"lane\":\"vless-reality\",\"transport\":\"tcp\",\"status\":\"prepared\"}"
    } else {
        "{\"lane\":\"invalid\",\"status\":\"rejected\"}"
    };

    env.new_string(outcome).expect("JNI string").into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeValidateProfile(
    mut env: JNIEnv,
    _class: JClass,
    profile_json: JString,
) -> jstring {
    let profile = env
        .get_string(&profile_json)
        .expect("profile json")
        .to_string_lossy()
        .into_owned();

    let valid = profile.contains("serverAddress") && profile.contains("publicKeyPresent");
    let outcome = if valid {
        "{\"valid\":true,\"reason\":\"ready for control-plane handshake\"}"
    } else {
        "{\"valid\":false,\"reason\":\"missing required Reality fields\"}"
    };

    env.new_string(outcome).expect("JNI string").into_raw()
}
