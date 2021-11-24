# Flex CLI

A command-line interface for [Sharetribe
Flex](https://www.sharetribe.com/flex/). Flex CLI enables you to
manage the transaction processes and transactional email templates of
your marketplaces.

To use Flex CLI, you will need a marketplace and an admin user API
key. You can request a marketplace from
[www.sharetribe.com/flex](https://www.sharetribe.com/flex/) to get
credentials to Console where you can generate new API keys in the
Build section.

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

If you're seeing `flex-cli: command not found` error on Windows and you installed Flex CLI with Yarn, you need to add Yarn global bin path to the PATH environment varible.

1. Run `yarn global bin` to see the global bin path
2. Add it to PATH environment variable
3. Restart command line

For a step-by-step guide with screenshots, have a look at this blog post: ['yarn global add' command does not work on Windows](https://sung.codes/blog/2017/12/30/yarn-global-add-command-not-work-windows/)

## Documentation

To get started with Flex CLI, see the [Getting started with Flex
CLI](https://www.sharetribe.com/docs/tutorials/getting-started-with-flex-cli/)
tutorial in Flex Docs.

## Issues

If you are having problems with Flex CLI, contact our support through
the widget in Console, or ask for help in the Flex developer Slack
forum.

## Development

Refer to the [README](./docs/README.md) for development.

## License

This project is licensed under the terms of Apache License, Version 2.0.

See the [LICENSE](LICENSE) file.
