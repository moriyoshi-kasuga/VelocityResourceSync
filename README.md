# VelocityResourceSync

VelocityResourceSync is a Minecraft Velocity plugin that listens for GitHub Webhooks and automatically performs a `git pull` to update the resource pack. This ensures that your server always has the latest resource pack from your GitHub repository.

## Features

- Listens for GitHub Webhooks.
- Automatically pulls the latest changes from the specified GitHub repository.
- Updates the resource pack on your Velocity server.

## Configuration

In the `config.yml` file, specify the GitHub repository and the branch and secret to pull.
see [config.yml](./src/main/resources/config.yml)
