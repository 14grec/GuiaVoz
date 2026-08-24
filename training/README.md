# Treinamento do cérebro local

O GuiaVoz 0.4 usa uma rede neural MLP com n-gramas de caracteres. Ela é treinada
em Python, exportada para um formato binário simples e executada em Java puro no
Android. Não há conexão com servidor durante a inferência.

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

O executor exige confiança maior para chamadas e mensagens. Quando a rede fica
abaixo do limiar, o parser determinístico antigo é usado como fallback seguro.
Um futuro Transformer quantizado poderá implementar a mesma interface
`NeuralIntentModel` sem alterar as ações Android.
