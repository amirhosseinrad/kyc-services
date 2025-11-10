#!/bin/bash
# deploy-dev.sh
IMAGE_NAME=kyc-services:latest
TAR_FILE=kyc-services-image.tar
REMOTE_HOST=192.168.179.21
REMOTE_USER=devops

docker save $IMAGE_NAME -o $TAR_FILE
scp $TAR_FILE $REMOTE_USER@$REMOTE_HOST:/home/$REMOTE_USER/
ssh $REMOTE_USER@$REMOTE_HOST "docker load -i /home/$REMOTE_USER/$TAR_FILE && docker rm -f kyc-services || true && docker run -d --name kyc-services -p 8002:8002 -p 5432:5432 $IMAGE_NAME"
