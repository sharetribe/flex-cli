# Sharetribe (Flex) CLI

A command-line interface for [Sharetribe](https://www.sharetribe.com/) (formerly Sharetribe Flex). Sharetribe CLI enables you to
manage the transaction processes and transactional email templates of
your marketplaces.

To use Sharetribe CLI, you will need a marketplace and an admin user API
key. You can create a Sharetribe marketplace from
[www.sharetribe.com/](https://www.sharetribe.com/) and get
credentials to Console where you can generate new API keys in [your account's "Manage API keys"](https://console.sharetribe.com/api-keys).

## Installation

Install with NPM:

``` bash
npm install --global flex-cli
```

or with Yarn:

``` bash
yarn global add flex-cli
```

To verify the installation and to see available commands, run:

``` bash
flex-cli
```

### Troubleshooting

#### flex-cli: command not found (on Windows)

If you're seeing `flex-cli: command not found` error on Windows and you installed Sharetribe CLI with Yarn, you need to add Yarn global bin path to the PATH environment varible.

1. Run `yarn global bin` to see the global bin path
2. Add it to PATH environment variable
3. Restart command line

For a step-by-step guide with screenshots, have a look at this blog post: ['yarn global add' command does not work on Windows](https://sung.codes/blog/2017/12/30/yarn-global-add-command-not-work-windows/)

## Documentation

To get started with Sharetribe CLI, see the [Getting started with Sharetribe
CLI](https://www.sharetribe.com/docs/introduction/getting-started-with-sharetribe-cli/)
tutorial in Sharetribe Docs.

## Issues

If you are having problems with Sharetribe CLI, contact our support through
the chat widget in Console, or ask for help in the Sharetribe Developer Slack
workspace.

## Development

Refer to the [README](./docs/README.md) for development.

## License

This project is licensed under the terms of Apache License, Version 2.0.

See the [LICENSE](LICENSE) file.
