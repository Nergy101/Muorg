<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <div
        class="mx-auto flex min-h-full w-full max-w-5xl flex-col items-center justify-center gap-4 px-6 py-8 lg:flex-row-reverse lg:gap-16 lg:px-12"
      >
        <div class="w-56 shrink-0 sm:w-64 lg:w-[26rem]">
          <OrcIllustration :variant="page.variant" />
        </div>

        <div class="flex w-full max-w-sm flex-col items-center text-center lg:max-w-md lg:items-start lg:text-left">
          <!-- The status code, with the middle 0 pressed as a record. -->
          <div
            class="flex items-center gap-1 text-[84px] font-bold leading-none text-on-background lg:text-[112px]"
            role="img"
            :aria-label="`Error ${code}`"
          >
            <span aria-hidden="true">{{ code[0] }}</span>
            <svg viewBox="0 0 100 100" class="h-[0.8em] w-[0.8em]" aria-hidden="true">
              <circle cx="50" cy="50" r="48" fill="#759346" />
              <circle cx="50" cy="50" r="38" fill="none" stroke="#5b7c32" stroke-width="2.5" opacity="0.6" />
              <circle cx="50" cy="50" r="30" fill="none" stroke="#5b7c32" stroke-width="2.5" opacity="0.6" />
              <circle cx="50" cy="50" r="17" fill="#e0b040" />
              <circle cx="50" cy="50" r="3.5" fill="#1c1917" />
              <path
                v-if="code === '500'"
                d="M62,4 L54,22 L66,32 L56,48 L72,60 L64,78 L76,94"
                stroke="#1c1917"
                stroke-width="4"
                stroke-linejoin="round"
                stroke-linecap="round"
                fill="none"
              />
            </svg>
            <span aria-hidden="true">{{ code[2] }}</span>
          </div>

          <h1 class="mt-3 text-balance text-headline-lg font-bold text-on-background lg:text-[40px] lg:leading-tight">
            {{ page.title }}
          </h1>
          <p class="mt-2 text-body-lg text-on-surface-variant">{{ page.body }}</p>

          <div class="mt-6 flex w-full flex-col gap-3 lg:w-auto lg:flex-row">
            <RouterLink
              v-if="page.primary.to"
              :to="page.primary.to"
              class="flex min-h-12 items-center justify-center rounded-full bg-primary px-8 text-label-lg font-bold text-on-primary"
            >
              {{ page.primary.label }}
            </RouterLink>
            <button
              v-else
              type="button"
              class="flex min-h-12 items-center justify-center rounded-full bg-primary px-8 text-label-lg font-bold text-on-primary"
              @click="reload"
            >
              {{ page.primary.label }}
            </button>
            <RouterLink
              :to="page.secondary.to"
              class="flex min-h-12 items-center justify-center rounded-full border-2 border-outline px-8 text-label-lg font-medium text-on-surface"
            >
              {{ page.secondary.label }}
            </RouterLink>
          </div>

          <p class="mt-6 text-body-sm text-on-surface-variant">Error {{ code }} · {{ page.status }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { RouteLocationRaw } from "vue-router";
import OrcIllustration, { type OrcVariant } from "../components/OrcIllustration.vue";

export type ErrorCode = "404" | "403" | "500" | "503";

interface ErrorPage {
  variant: OrcVariant;
  status: string;
  title: string;
  body: string;
  /** No `to` means the button reloads the app instead of navigating. */
  primary: { label: string; to?: RouteLocationRaw };
  secondary: { label: string; to: RouteLocationRaw };
}

const LIBRARY = { label: "Back to your library", to: { name: "library" } } as const;

const PAGES: Record<ErrorCode, ErrorPage> = {
  "404": {
    variant: "not-found",
    status: "Page not found",
    title: "This track skipped.",
    body: "We couldn't find that page. It may have moved to another record, or it was never pressed.",
    primary: LIBRARY,
    secondary: { label: "Search for a track", to: { name: "search" } },
  },
  "403": {
    variant: "forbidden",
    status: "Forbidden",
    title: "Backstage pass needed.",
    body: "The server didn't accept your key for this part of the library. Check your connection settings.",
    primary: { label: "Open settings", to: { name: "settings" } },
    secondary: LIBRARY,
  },
  "500": {
    variant: "server-error",
    status: "Internal server error",
    title: "Something went off-key.",
    body: "The server hit a sour note. Give it a moment, then try again.",
    primary: { label: "Try again" },
    secondary: LIBRARY,
  },
  "503": {
    variant: "maintenance",
    status: "Service unavailable",
    title: "Tuning up. Back soon.",
    body: "Muorg is getting a quick tune-up. This usually only takes a few minutes.",
    primary: { label: "Check again" },
    secondary: LIBRARY,
  },
};

const props = defineProps<{ code: ErrorCode }>();

const page = computed(() => PAGES[props.code]);

function reload(): void {
  window.location.reload();
}
</script>
