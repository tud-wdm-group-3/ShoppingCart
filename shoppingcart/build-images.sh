#!/bin/bash
# Builds all the images and pushes them to DockerHub.
# Make sure you are logged in with the docker cli (and change the handle)

DOCKERHUB_HANDLE=ngavalas

build_push() {
  img_name="$1"
  tag="${2:-latest}"
  docker build -t "${img_name}:${tag}" -f "Dockerfile-${img_name}" . \
    && docker tag "${img_name}:${tag}" "${DOCKERHUB_HANDLE}/${img_name}:${tag}" \
    && docker push "${DOCKERHUB_HANDLE}/${img_name}:${tag}"
  #minikube image load "${img_name}:${tag}"
}

build_push order
build_push payment
build_push stock
