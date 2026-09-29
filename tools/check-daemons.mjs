#!/usr/bin/env node
/**
 * Проверка окружения сборки: старые демоны Gradle/Kotlin и свободная память.
 *
 * Зачем: на машине 7.8 ГБ два простаивающих демона по ~1.9 ГБ (куча 4 ГБ, настройки до 29.09.2026)
 * выбирали память целиком — сборка уходила в своп, тестовый воркер падал, а «пустяковый» прогон
 * одного модуля занимал минуты. Демон Gradle сам умирает по `org.gradle.daemon.idletimeout`
 * (10 минут), но зависшие и чужие процессы так не убираются.
 *
 * Использование:
 *   node tools/check-daemons.mjs            — показать состояние, вернуть 1, если есть старые демоны
 *                                             или мало памяти (годится для проверки перед сборкой);
 *   node tools/check-daemons.mjs --kill     — дополнительно остановить старые демоны и Kotlin-демон.
 *
 * Порог возраста для «старых» — 30 минут (`--age=15` меняет порог), минимум свободной памяти — 1 ГБ.
 */
import { execFileSync } from 'node:child_process'

const AGE_MINUTES = Number(process.argv.find((a) => a.startsWith('--age='))?.slice(6) ?? 30)
const KILL = process.argv.includes('--kill')
const MIN_FREE_MB = 1024

function run(command, args) {
  try {
    return execFileSync(command, args, { encoding: 'utf8' })
  } catch {
    return ''
  }
}

/** Демоны и процессы сборки: pid, возраст в секундах, память в КБ, командная строка. */
function buildProcesses() {
  return run('ps', ['-eo', 'pid=,etimes=,rss=,args='])
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const match = /^(\d+)\s+(\d+)\s+(\d+)\s+(.*)$/.exec(line)
      if (!match) return null
      const [, pid, seconds, rssKb, command] = match
      if (!/java|gradle|kotlin/i.test(command)) return null
      const kind = /KotlinCompileDaemon|kotlin-daemon/i.test(command)
        ? 'kotlin-демон'
        : /GradleDaemon|gradle\.launcher\.daemon/i.test(command)
          ? 'демон Gradle'
          : /GradleWrapperMain|gradlew/i.test(command)
            ? 'клиент сборки'
            : 'процесс JVM'
      return { pid: Number(pid), ageMinutes: Math.round(Number(seconds) / 60), rssMb: Math.round(Number(rssKb) / 1024), kind }
    })
    .filter(Boolean)
}

function freeMemoryMb() {
  const line = run('free', ['-m']).split('\n').find((l) => l.startsWith('Mem:'))
  return line ? Number(line.trim().split(/\s+/)[3]) : Number.NaN
}

const processes = buildProcesses()
const freeMb = freeMemoryMb()
const stale = processes.filter((p) => /демон/.test(p.kind) && p.ageMinutes >= AGE_MINUTES)
const totalMb = processes.reduce((sum, p) => sum + p.rssMb, 0)

console.log('процессы сборки:')
for (const p of processes.sort((a, b) => b.rssMb - a.rssMb)) {
  console.log(`  ${String(p.pid).padStart(7)}  ${String(p.ageMinutes).padStart(4)} мин  ${String(p.rssMb).padStart(5)} МБ  ${p.kind}`)
}
if (processes.length === 0) console.log('  (нет)')
console.log(`итого память процессов сборки: ${totalMb} МБ, свободно в системе: ${freeMb} МБ`)

let failed = false
if (stale.length > 0) {
  console.log(`\nстарые демоны (≥ ${AGE_MINUTES} мин): ${stale.map((p) => p.pid).join(', ')}`)
  failed = true
}
if (freeMb < MIN_FREE_MB) {
  console.log(`\nсвободной памяти меньше ${MIN_FREE_MB} МБ — сборка уйдёт в своп`)
  failed = true
}

if (KILL && (stale.length > 0 || freeMb < MIN_FREE_MB)) {
  for (const p of stale) {
    try {
      process.kill(p.pid, 'SIGTERM')
      console.log(`остановлен ${p.kind} pid=${p.pid}`)
    } catch (error) {
      console.log(`не удалось остановить pid=${p.pid}: ${error.message}`)
    }
  }
  console.log('\nдальше: ./gradlew --stop — убирает и демоны Gradle, и клиентов сборки')
}

process.exit(failed && !KILL ? 1 : 0)
