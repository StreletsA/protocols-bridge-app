# Protocols Bridge
## Example of using QUIC and RSocket protocols in synchronous interaction

### Interaction scheme
![Scheme](/protocols-bridge-scheme.png "Scheme")

### Stack
- Java 18
- Spring Boot (starters: web, rsocket)
- Netty (HTTP, HTTP3)

### Properties (System variables)
- bridge.protocol (BRIDGE_PROTOCOL): main protocol of bridge (quic or rsocket)
- bridge.quic.server.enabled (BRIDGE_QUIC_SERVER_ENABLED): QUIC server startup flag (true or false)
- bridge.quic.server.host (BRIDGE_QUIC_SERVER_HOST): host on which the QUIC-server is located (destination for requests of QUIC-client)
- bridge.quic.server.port (BRIDGE_QUIC_SERVER_PORT): port on which the QUIC-server is started
- bridge.quic.client.port (BRIDGE_QUIC_CLIENT_PORT): port on which the QUIC-client is binded
- bridge.rsocket.server.host (BRIDGE_RSOCKET_SERVER_HOST): host on which the RSocket-server is located
- bridge.rsocket.server.port (BRIDGE_RSOCKET_SERVER_PORT): port on which the RSocket-server is started
- bridge.rsocket.channel (BRIDGE_RSOCKET_CHANNEL): messaging channel for RSocket