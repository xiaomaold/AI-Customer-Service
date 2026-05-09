<template>
  <div class="project-mark" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect x="4" y="4" width="56" height="56" rx="18" fill="url(#bg)" />
      <path
        d="M32 15.5C26.7 15.5 22.3 19.5 21.7 24.6C17.2 26 14.5 29.7 14.5 33.9C14.5 40 19.2 44.5 25.2 44.5H26.5"
        stroke="white"
        stroke-width="4.2"
        stroke-linecap="round"
      />
      <path
        d="M20.5 19.5C23.2 16.9 27.1 15.5 31.2 15.5C38.5 15.5 44.6 20.6 45.9 27.4C50.6 28.6 54 32.4 54 37.1C54 42.9 49.2 47.5 43.2 47.5H39.5"
        stroke="white"
        stroke-width="4.2"
        stroke-linecap="round"
        opacity="0.9"
      />
      <path
        d="M24 35.3C24 30.8 27.6 27.2 32 27.2C36.4 27.2 40 30.8 40 35.3C40 39.7 36.4 43.3 32 43.3C27.6 43.3 24 39.7 24 35.3Z"
        stroke="white"
        stroke-width="4.2"
      />
      <circle cx="32" cy="35.3" r="3.5" fill="white" />
      <defs>
        <linearGradient id="bg" x1="8" y1="8" x2="56" y2="56" gradientUnits="userSpaceOnUse">
          <stop stop-color="#2F5D8A" />
          <stop offset="1" stop-color="#334155" />
        </linearGradient>
      </defs>
    </svg>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), {
  size: 34
})
</script>

<style scoped>
.project-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

svg {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
