
from fastapi import FastAPI
from pydantic import BaseModel
from google import genai
from google.genai import types
import os

app = FastAPI()

client = genai.Client(
    api_key=os.environ["GEMINI_API_KEY"]
)

class Pergunta(BaseModel):
    mensagem: str

@app.get("/")
def inicio():
    return {"status": "Jarvis online"}

@app.post("/conversar")
def conversar(pergunta: Pergunta):
    resposta = client.models.generate_content(
        model="gemini-3.5-flash-lite",
        contents=pergunta.mensagem,
        config=types.GenerateContentConfig(
            system_instruction=(
                "Você é Jarvis, um assistente pessoal inteligente, "
                "educado, natural, prestativo e bem-humorado. "
                "Fale sempre em português brasileiro."
            )
        )
    )

    return {"resposta": resposta.text}
