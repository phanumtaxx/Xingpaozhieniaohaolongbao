super chinese client

## Development login

Run `launch-client.bat` or `gradlew.bat runClient`. DevAuth is enabled by default.
On the first authenticated launch, follow the Microsoft sign-in link printed in
the console. Later launches reuse the saved account session.

The account configuration lives in `run/devauth/config.toml`. Existing account
configuration is preserved. For an offline development launch, use
`gradlew.bat runClient -PdevAuth=false`.

DevAuth setup follows the [official documentation](https://github.com/DJtheRedstoner/DevAuth#configuration).
