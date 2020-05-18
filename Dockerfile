FROM fholzer/nginx-brotli

# copy exporter
COPY --from=wetransform/prometheus-nginxlog-exporter:custom-20200518 /prometheus-nginxlog-exporter /prometheus-nginxlog-exporter

ADD ./wrapper.sh /wrapper.sh
ADD ./default.yml /exporter.yml

ENV EXPORTER_CONFIG /exporter.yml

CMD "/wrapper.sh"
