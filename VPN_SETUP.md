# Deeprows VPN setup

This release changes the app from a fake/placeholder VPN concept to a production-oriented architecture. The Android app requests `VpnService` permission and contains the country/server/session contracts, but it intentionally does not claim traffic is protected until a real WireGuard gateway and HTTPS control API are configured.

## Recommended rollout

1. Deploy one Linux WireGuard gateway in the United States.
2. Give it a stable public hostname such as `us-ny-01.vpn.deeprows.com`.
3. Configure IP forwarding + NAT on the gateway.
4. Keep the gateway private key only on the gateway.
5. Build an HTTPS Deeprows control API with these operations:
   - `GET /vpn/servers`
   - `POST /vpn/session`
   - `DELETE /vpn/session/{id}`
6. `POST /vpn/session` should authenticate the user, select a healthy server, and return a short-lived WireGuard client configuration.
7. Integrate that returned configuration with the WireGuard Android tunnel engine.
8. Verify the public IP, DNS behavior, IPv4/IPv6 routing, reconnects, and kill-switch behavior before advertising the VPN as active.
9. Add additional countries one at a time; the app already generates the full ISO country list.

## Important

- Never commit WireGuard private keys to GitHub.
- Never embed permanent server credentials in the APK.
- Do not mark a country `available=true` until a real gateway exists.
- Content Country and VPN Location are intentionally separate: content localization does not change the user's network IP.
- Android permits one active VPN connection at a time.

## Server model

Start with one gateway per country, then add multiple gateways and health checks. The API should return the best healthy gateway for the requested country so the Android app does not need a hard-coded server inventory.
