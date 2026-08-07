from django.apps import AppConfig


class AtpBridgeConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.atp_bridge"
    label = "atp_bridge"
    verbose_name = "ATP Bridge API"
