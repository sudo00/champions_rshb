<script setup lang="ts">
const route = useRoute();
const slug = computed(() => String(route.params.slug ?? ""));
</script>

<template>
  <main class="page">
    <p class="eyebrow">Карточка вина</p>
    <h1>{{ slug || "wine" }}</h1>
    <article class="card">
      <p>Производитель, регион, сорт, рейтинг Роскачества и гастрономическая подсказка появятся после импорта каталога.</p>
      <NuxtLink to="/">Новый снимок</NuxtLink>
    </article>
  </main>
</template>
