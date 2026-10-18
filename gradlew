#!/bin/sh
# Wrapper simples que usa o Gradle instalado no sistema (GitHub Actions tem Gradle)
exec gradle "$@"
