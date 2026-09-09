<script setup lang="ts">
/** Keyboard tab: the shortcut reference. */
import FeatherIcon from "@shared/components/FeatherIcon.vue";
</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="command"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Keyboard
              </p>
              <div class="settings-section space-y-2">
                <p class="text-xs text-stone-400">
                  Keyboard shortcuts are currently fixed. Planned improvements
                  include per-action customization and profile export/import.
                </p>
                <p class="text-[11px] text-stone-500">
                  These are the same shortcuts shown in the key map:
                </p>
                <dl class="mt-1 space-y-2 text-xs">
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+F / ⌘F
                    </dt>
                    <dd class="min-w-0 text-stone-300">Focus search bar</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+R / ⌘R
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Refresh whole library (all folders, all reports)
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+M / ⌘M
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Toggle metadata editor panel for current selection
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+L / ⌘L
                    </dt>
                    <dd class="min-w-0 text-stone-300">Show library panel</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+P / ⌘P
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Toggle full player panel
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+A / ⌘A
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Select all tracks in current view and enable multi-select
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Ctrl+K / ⌘K
                    </dt>
                    <dd class="min-w-0 text-stone-300">Open key map</dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Escape
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Close metadata editor panel or cover popup
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      ↓ Arrow Down
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Move focus down in track list
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      ↑ Arrow Up
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      Move focus up in track list
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Space
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      On group row: expand or collapse. On track row: select
                      (add to selection in multi-select).
                    </dd>
                  </div>
                  <div class="flex gap-3">
                    <dt class="w-40 shrink-0 font-mono text-stone-400">
                      Enter
                    </dt>
                    <dd class="min-w-0 text-stone-300">
                      With one track selected: start playback or pause if
                      already playing.
                    </dd>
                  </div>
                </dl>
              </div>
            </div>

</template>
