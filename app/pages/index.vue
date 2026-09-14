<template>
  <main class="page">
    <header>
      <p class="eyebrow">Своё вино</p>
      <h1>Сканер этикетки</h1>
      <p>Сфотографируйте бутылку у полки — сервис вернёт одну карточку вина.</p>
    </header>

    <form class="card" @submit.prevent>
      <label class="upload">
        <span>Камера или галерея</span>
        <input type="file" accept="image/*" capture="environment" disabled>
      </label>
      <p class="hint">Загрузка и поиск подключим после CV-пайплайна. Сейчас API-заглушки: <code>POST /api/scan</code>, <code>POST /api/eval</code>.</p>
    </form>
  </main>
</template>
