use std::io;
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RealityConfig {
    pub server_address: String,
    pub server_port: u16,
    pub uuid: [u8; 16],
    pub sni: String,
    pub public_key: String,
    pub short_id: String,
    pub network: String,
}

#[derive(Debug)]
pub struct RealitySession {
    stream: TcpStream,
    config: RealityConfig,
}

impl RealityConfig {
    pub fn parse_uuid(value: &str) -> Result<[u8; 16], String> {
        let compact = value.replace('-', "");
        if compact.len() != 32 || !compact.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err("invalid VLESS UUID".to_owned());
        }
        let mut result = [0u8; 16];
        for (index, byte) in result.iter_mut().enumerate() {
            *byte = u8::from_str_radix(&compact[index * 2..index * 2 + 2], 16)
                .map_err(|_| "invalid VLESS UUID".to_owned())?;
        }
        Ok(result)
    }

    pub fn build_vless_tcp_request(&self) -> Result<Vec<u8>, String> {
        let host = self.server_address.as_bytes();
        if host.is_empty() || host.len() > 255 {
            return Err("invalid VLESS target host".to_owned());
        }
        let mut request = Vec::with_capacity(32 + host.len());
        request.push(0x01); // VLESS version
        request.extend_from_slice(&self.uuid);
        request.push(0x00); // addons length, no addons
        request.push(0x01); // TCP command
        request.extend_from_slice(&self.server_port.to_be_bytes());
        request.push(0x02); // domain address type
        request.push(host.len() as u8);
        request.extend_from_slice(host);
        Ok(request)
    }

    pub fn connect_tcp(&self, timeout: Duration) -> Result<RealitySession, String> {
        let address = format!("{}:{}", self.server_address, self.server_port);
        let socket = address
            .to_socket_addrs()
            .map_err(|_| "DNS resolution failed".to_owned())?
            .next()
            .ok_or_else(|| "no resolved address".to_owned())?;
        let stream = TcpStream::connect_timeout(&socket, timeout)
            .map_err(|error| format!("TCP connect failed: {error}"))?;
        stream
            .set_read_timeout(Some(timeout))
            .map_err(|error| format!("failed to set read timeout: {error}"))?;
        stream
            .set_write_timeout(Some(timeout))
            .map_err(|error| format!("failed to set write timeout: {error}"))?;
        Ok(RealitySession {
            stream,
            config: self.clone(),
        })
    }
}

impl RealitySession {
    pub fn tcp_stream(&self) -> &TcpStream {
        &self.stream
    }

    pub fn config(&self) -> &RealityConfig {
        &self.config
    }

    /// Reality TLS is intentionally not replaced with ordinary TLS here.
    /// A standard TLS client would be fingerprintable and would violate the
    /// transport contract. This fail-closed boundary is where the verified
    /// Reality-compatible ClientHello and VLESS exchange belong.
    pub fn perform_reality_handshake(&mut self) -> Result<(), io::Error> {
        Err(io::Error::new(
            io::ErrorKind::Unsupported,
            "Reality-compatible TLS handshake is not implemented yet",
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn config() -> RealityConfig {
        RealityConfig {
            server_address: "example.com".to_owned(),
            server_port: 443,
            uuid: RealityConfig::parse_uuid("00000000-0000-0000-0000-000000000001").unwrap(),
            sni: "example.com".to_owned(),
            public_key: "public-key".to_owned(),
            short_id: "short".to_owned(),
            network: "tcp".to_owned(),
        }
    }

    #[test]
    fn parses_uuid_and_frames_domain_target() {
        let request = config().build_vless_tcp_request().unwrap();
        assert_eq!(&request[..18], &[0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0]);
        assert_eq!(&request[18..24], &[0x01, 0x01, 0xbb, 0x02, 0x0b, b'e']);
    }

    #[test]
    fn rejects_bad_uuid() {
        assert!(RealityConfig::parse_uuid("nope").is_err());
    }
}
