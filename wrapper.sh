#!/bin/sh
#
# Very simple wrapper script

# remove symbolic link
rm /var/log/nginx/access.log

# create real file
touch /var/log/nginx/access.log

echo "Starting log exporter..."
./prometheus-nginxlog-exporter -config-file $EXPORTER_CONFIG &
# TODO handle that process exiting?

echo "Starting nginx"
nginx -g "daemon off;"
