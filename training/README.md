# Treinamento do cérebro local

O GuiaVoz 0.5 usa uma rede neural hierárquica multi-saída com n-gramas de
caracteres. Ela prevê ação, objeto, canal e intenção terminal, e um decodificador
restrito impede combinações impossíveis. O treino ocorre em Python antes da
compilação; a inferência roda em Java puro e offline no Android.

## Treinar

```bash
python -m pip install -r training/requirements.txt
python training/test_brain.py
python training/train_brain.py
python training/check_quality.py --minimum 0.90
```

Arquivos:

- `intents.csv`: corpus de treinamento revisável;
- `intents_eval.csv`: avaliação separada, nunca usada para ajustar os pesos;
- `train_brain.py`: normalização, ampliação do treino, rede e exportação;
- `training_report.json`: métricas e erros da última execução;
- `app/src/main/assets/guiavoz_brain.bin`: pesos carregados pelo APK.

O relatório separa acurácia decodificada, intenção terminal, cada cabeça e a
combinação estrutural bruta. O gate também mede isoladamente enviar, responder,
ler, ouvir e ligar pelo WhatsApp. O executor exige confiança maior para chamadas
e mensagens e sempre pede confirmação antes de produzir um efeito externo.

Comandos desconhecidos e correções explícitas (“você entendeu errado”) ficam no
arquivo privado `misunderstood_commands.jsonl` do aplicativo. Ele não é enviado
automaticamente; pode ser incorporado ao corpus somente depois de revisão e
anonimização.
