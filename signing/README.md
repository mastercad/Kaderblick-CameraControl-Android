# Release signing

The local release signing files are stored beside this document:

- `cameracontrol-release.jks`: release keystore
- `credentials`: password used for both the keystore and its private key

Both files are ignored by Git and must never be committed. Their local file
permissions are restricted to the repository owner.

Signing metadata:

- Key alias: `cameracontrol-release`
- Certificate SHA-256: `a343fd6858b3cd771ba58a04f8fe6aeb699d4c635eea0368b6739a9b22990ba3`

GitHub Actions uses the corresponding repository secrets. The additional
backup is stored in `/home/andreas/.local/share/cameracontrol-signing/`.
