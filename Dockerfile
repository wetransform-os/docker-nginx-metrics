FROM fholzer/nginx-brotli

# copy exporter
COPY --from=quay.io/martinhelmich/prometheus-nginxlog-exporter:v1.4.1 /prometheus-nginxlog-exporter /prometheus-nginxlog-exporter

ADD ./wrapper.sh /wrapper.sh
ADD ./default.yml /exporter.yml

ENV EXPORTER_CONFIG /exporter.yml

CMD "/wrapper.sh"
