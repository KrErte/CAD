{{- define "ai-cad.image" -}}
{{ .Values.image.registry }}/cad-{{ .component }}:{{ .Values.image.tag }}
{{- end -}}
