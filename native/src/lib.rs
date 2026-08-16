use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

/// Minimal, intentionally boring JNI boundary for milestone 1.
/// Do not pass secrets through logcat or return raw profile material.
#[no_mangle]
pub extern "system" fn Java_com_shadevpn_android_NativeBridge_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let value = env.new_string("shadevpn-native/0.1.0").expect("JNI string");
    value.into_raw()
}
