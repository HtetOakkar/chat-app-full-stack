import requests
import json
import os

api_key = "re_523Frsx7_Hn3mGa4BC4KXCnqy592LVCc8re_523Frsx7_Hn3mGa4BC4KXCnqy592LVCc8"
headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
payload = {
    "from": "onboarding@resend.dev",
    "to": ["test@example.com"],
    "subject": "Test",
    "template_id": "tpl_verification"
}

resp = requests.post("https://api.resend.com/emails", headers=headers, json=payload)
print(resp.status_code, resp.text)
