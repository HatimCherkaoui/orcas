#!/usr/bin/env bash
# ApiVersions v0 with request header v1: a Kafka protocol check without launching a JVM.
set -euo pipefail
exec 3<>/dev/tcp/127.0.0.1/9092
# Length=10, API key=18, version=0, correlation=1, nullable client ID=-1.
printf '\x00\x00\x00\x0a\x00\x12\x00\x00\x00\x00\x00\x01\xff\xff' >&3
response=$(head -c 10 <&3 | od -An -tx1 | tr -d ' \n')
[[ ${#response} -eq 20 && ${response:8:8} == 00000001 && ${response:16:4} == 0000 ]]
