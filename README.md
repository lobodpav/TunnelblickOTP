# Allows Tunnelblick to authenticate via user name and a generated OTP token
The script allows Tunnelblick to get a new OTP token every time it tries to connect, and uses the token instead of a password.
The OTP is based on a 2FA secret key stored in KeyChain, and the generated OTP is stored in the KeyChain as well.

The generated OTP is compatible with Authy and Google Authenticator.

# Installation instructions
- `brew install groovy`
- `brew install oath-toolkit`
- Copy all the files into your Tunnelblick configuration. For example into: `~/Library/Application Support/Tunnelblick/Configurations/YourVPNConfig.tblk/Contents/Resources/`
- Connect to the VPN
- First connection will fail. Your credentials will be created in the KeyChain under `Tunnelblick-Auth-YourVPNConfig` entries.
  - Add your user name into the `password` field of the `username` entry
  - Add your 2FA secret key (the one from QR code) into the `password` field of the `secret` entry
- Reconnect the VPN

# Tested configuration
- macOS Mojave 10.14.2
- Tunnelblick 3.7.8
- Groovy 2.5.5 under Oracle JVM 1.8.0_201
- OATH Toolkit 2.6.2

# Implementation details
- The `pre-connect.sh` script is executed by Tunnelblick prior to any connection attempt
- The Shell script calls the Groovy script, which
  - Writes a configuration that instructs Tunnelblick to retrieve `username` and `password` from KeyChain
  - Reads 2FA `secret` from KeyChain
  - Generates OTP token based on the 2FA secret
  - Stores the OTP token into `password` field in the KeyChain
- Tunnelblick reads the `username` and `password` to authenticate

## Additional notes
- The KeyChain entries are created in the system chain because Tunnelblick runs the `pre-connect.sh` script under the `root` user
- The Tunnelbkick's `YourVPNConfig-keychainHasUsernameAndPassword` configuration is stored under currently logged in user
