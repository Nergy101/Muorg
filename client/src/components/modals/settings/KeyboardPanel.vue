<script setup lang="ts">
/** Keyboard tab: the shortcut reference. Rendered from the shared key map source. */
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { KEY_MAP_ENTRIES } from "@shared/utils/keyMap";
</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-on-surface-variant"
              >
                <FeatherIcon
                  name="command"
                  class="h-3.5 w-3.5 shrink-0 text-on-surface-variant"
                />
                Keyboard
              </p>
              <div class="settings-section space-y-2">
                <p class="text-xs text-on-surface-variant">
                  Keyboard shortcuts are currently fixed. Planned improvements
                  include per-action customization and profile export/import.
                </p>
                <p class="text-[11px] text-on-surface-variant">
                  These are the same shortcuts shown in the key map:
                </p>
                <dl class="mt-1 space-y-2 text-xs">
                  <div
                    v-for="entry in KEY_MAP_ENTRIES"
                    :key="entry.keys"
                    class="flex gap-3"
                  >
                    <dt class="w-40 shrink-0 font-mono text-on-surface-variant">
                      {{ entry.keys }}
                    </dt>
                    <dd class="min-w-0 text-on-surface">
                      {{ entry.description }}
                    </dd>
                  </div>
                </dl>
              </div>
            </div>

</template>
