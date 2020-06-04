FROM fholzer/nginx-brotli

# setup s6 overlay
# see https://github.com/just-containers/s6-overlay
# and https://skarnet.org/software/s6/
ADD https://github.com/just-containers/s6-overlay/releases/download/v2.0.0.1/s6-overlay-amd64.tar.gz /tmp/
RUN gunzip -c /tmp/s6-overlay-amd64.tar.gz | tar -xf - -C /

# copy exporter
COPY --from=wetransform/prometheus-nginxlog-exporter:custom-20200518 /prometheus-nginxlog-exporter /prometheus-nginxlog-exporter

# add service definitions
COPY ./services.d /etc/services.d

# remove symbolic link for access log and create real file
RUN rm /var/log/nginx/access.log && touch /var/log/nginx/access.log

ADD ./default.yml /exporter.yml

ENV EXPORTER_CONFIG /exporter.yml

ENTRYPOINT ["/init"]
