# Management — MeshFabric

[Español](es/profile.md) | [Project README](../README.md)

Management replaces the former Profile label. Its four tabs are My Account, Device Groups, Users and Groups (user groups). Device groups and user groups are different entities.

## Available functionality

- Agent-group actions provide Add agent and installation-link invitations: see [Agent installation and invitations](agent-installation.md).

- Native account-security and preference controls: see [My Account](profile-native.md).
- Native users, user groups, memberships and device-group access: see [User and access administration](profile-user-administration.md).
- Device-group details include summary, devices and received access. Advanced settings cover supported flags, consent and inactivity expiry.
- User details link to received memberships, common device groups and visible devices. Navigation uses exact internal identifiers and available records, not names alone. Returning restores a valid originating view when possible.
- Connection indicators and outer spacing are consistent across the four Management and four Server tabs. Each indicator reports its own channel; it is not a statement that every channel is connected.

## Safety and scope

Server permissions and policy remain authoritative. Missing records or failed reads mean unavailable data, not an empty inventory or proof of deletion. Common-access views are based on received visible data, not a complete effective-permission calculation.

Sensitive access changes use reviewed data and separate confirmation. Changed identity, permissions, data or connection can invalidate the review. Unknown outcomes are not success and writes are not automatically retried. Reads and writes are not atomic; concurrent administrator changes remain possible.

Native FIDO2 sign-in and new security-key registration remain pending. Listing/removing existing keys is available. Automated checks do not replace live-server and physical-device validation of every operation.
