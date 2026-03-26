#!/bin/bash
# =============================================================================
# compile.sh — Compila todos os arquivos Java do projeto DiscordLP2
# =============================================================================
set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Compilando DiscordLP2..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Cria a pasta de saída (classes compiladas)
mkdir -p out

# Compila todos os .java da pasta src/
javac -d out -sourcepath . src/*.java

echo "✓ Compilação concluída! Classes em ./out/"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  Para iniciar o SERVIDOR:"
echo "    ./run_server.sh"
echo ""
echo "  Para iniciar um CLIENTE:"
echo "    ./run_client.sh"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
