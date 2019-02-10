#!/usr/bin/env groovy

import groovy.transform.SourceURI

/**
 * A script allowing Tunnelblick to use 2FA token instead of a password
 */

/**
 * Executes a shell command.
 * If the execution fails, the error stream is printed out and the whole script execution is terminated.
 * @param command Array of a command and its arguments. For example ["ls", "-la"]
 * @param errorMessage Prefix to the error message in case the execution fails
 * @param failOnError Terminates the script execution if true, returns null if false
 * @return Content of the Output stream generated by the command
 */
String executeCommand(String[] command, String errorMessage = "", boolean failOnError = true) {
    def exec = command.execute()
    int code
    if ((code = exec.waitFor()) != 0) {
        System.err.println((errorMessage.empty ? "Error: " : "${errorMessage}: ") + exec.errorStream.text)

        if (failOnError) {
            System.exit(code)
        } else {
            return null
        }
    }

    // remove trailing newline
    return exec.text - ~/\n$/
}

/**
 * Gets the `password`s field content out of a KeyChain entry
 * @param accountName Either `username`, `password`, or `secret`
 * @param failOnError Terminates the script execution if true, returns null if false
 * @return Content of the entry's `password` field
 */
String getKeyChainEntry(String accountName, boolean failOnError = true) {
    String[] cmd = ["/usr/bin/security", "find-generic-password", "-ws", tunnelblickKeyName, "-a", accountName]
    return executeCommand(cmd, "Failed to retrieve a KeyChain entry named '$tunnelblickKeyName' with '$accountName' account", failOnError)
}

/**
 * Updates the `password` field of an existing KeyChain entry
 * @param accountName Either `username`, `password`, or `secret`
 * @param newValue A new value of the entry
 */
void updateKeyChainEntry(String accountName, String newValue) {
    String[] cmd = ["/usr/bin/security", "add-generic-password", "-Us", tunnelblickKeyName, "-a", accountName, "-w", newValue]
    executeCommand(cmd, "Failed to update the KeyChain entry named '$tunnelblickKeyName' with '$accountName' account")
}

/**
 * Creates a new KeyChain entry, assigns a value to the `password` field,
 * and grants required access permissions to `security` and `Tunnelblick` apps.
 * @param accountName Either `username`, `password`, or `secret`
 * @param value Value of the new entry
 * @param comment Comment of the new entry
 */
void createNewKeyChainEntry(String accountName, String value, String comment = "") {
    String[] cmd = ["/usr/bin/security", "add-generic-password", "-Us", tunnelblickKeyName, "-a", accountName, "-w", value,
                    "-j", comment, "-T", "/usr/bin/security", "-T", "/Applications/Tunnelblick.app"]
    executeCommand(cmd, "Failed to create a new KeyChain entry named '$tunnelblickKeyName' with '$accountName' account")
}

/**
 * Creates a KeyChain entry if it does not exists
 * @param accountName Either `username`, `password`, or `secret`
 * @param comment Comment of the new entry
 * @return True if a new entry was created, false if it already existed
 */
boolean initKeyChainEntry(String accountName, String comment = "") {
    def password = getKeyChainEntry(accountName, false)
    if (password == null) {
        createNewKeyChainEntry(accountName, "ChangeTheValue", comment)
        return true
    }
    return false
}

/**
 * Generates an OTP token, 6 numbers long, compatible with Authy and Google Authenticator
 * @param secret The 2FA secret the resulting token is based on
 * @return Generated OTP token
 */
String generateOTPToken(String secret) {
    String[] cmd = ["/usr/local/bin/oathtool", "--totp", "-b", "-d", "6", secret]
    return executeCommand(cmd, "Failed to generate OTP token")
}


// --- Script execution part ---


/// URI of this script
@SourceURI
URI sourceUri
if (sourceUri == null) {
    System.err.println "Location the this script is unknown"
    System.exit(10)
}

def path = sourceUri.path
def loggedInUser = System.getenv().get("USER")
if (loggedInUser == null) {
    System.err.println "Failed to retrieve currently logged in user"
    System.exit(20)
}

// Name of the Tunnelblick's connection
def connectionName = ""
if ((match = path =~ /.*\/([^\/]*)\.tblk\/.*/)) {
    connectionName = match.group(1)
} else {
    System.err.println "Failed to find connection name in the '$path' path"
    System.exit(30)
}

// Configure Tunnelblick to read the username and password from keychain
String[] cmd = ["sudo", "-Hiu", loggedInUser, "defaults", "write", "net.tunnelblick.tunnelblick", "${connectionName}-keychainHasUsernameAndPassword", "-bool", "YES"]
executeCommand(cmd, "Failed to modify Tunnelblick's settings")


// KeyChain name where Tunnelblick stores its records
tunnelblickKeyName = "Tunnelblick-Auth-$connectionName"

// Create `username`, `secret`, and `password` KeyChain entries if these do not exist yet
// to ease the initial setup for the user
def err1 = initKeyChainEntry("username", "Replace the password field by your user name")
def err2 = initKeyChainEntry("secret", "Replace the password field by your secret 2FA key")
def err3 = initKeyChainEntry("password", "An OTP token will be generated automatically into the password field")

if (err1 || err2 || err3) {
    System.err.println """\
ERROR: KeyChain did not contain required credentials.
'$tunnelblickKeyName' keys have been created with default values.
Alter the 'username' and 'secret' accounts, and reconnect the VPN
"""
    System.exit(40)
}


def secret = getKeyChainEntry("secret")
def otp = generateOTPToken(secret)

// Store the OTP token to KeyChain so that Tunnelblick can pick it up
updateKeyChainEntry("password", otp)