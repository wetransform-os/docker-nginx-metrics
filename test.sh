#!/bin/bash

docker build -t wetransform/nginx-metrics .

docker run --rm -p 6387:6387 -p 8080:80 -it wetransform/nginx-metrics
