<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, watch, onBeforeUnmount } from "vue";
import { MicOff } from "@lucide/vue";
import { t } from "../ui.js";
const props = defineProps({
  person: { type: Object, required: true },
  screen: Boolean,
});
const element = ref(null);
let attached, attachedElement;
const detach = () => {
  if (attached && attachedElement) attached.detach(attachedElement);
  attached = null;
  attachedElement = null;
};
watch(
  [element, () => (props.screen ? props.person.screen : props.person.camera)],
  () => {
    detach();
    const track = props.screen ? props.person.screen : props.person.camera;
    if (track && element.value) {
      track.attach(element.value);
      element.value.muted = true;
      attached = track;
      attachedElement = element.value;
    }
  },
  { flush: "post" },
);
onBeforeUnmount(detach);
</script>
<template>
  <article
    :class="['video-tile', { speaking: person.speaking, screen }]"
    :aria-label="
      person.name + (screen ? ' ' + t('屏幕共享', 'screen share') : '')
    "
  >
    <video
      v-if="screen ? person.screen : person.camera"
      ref="element"
      autoplay
      playsinline
      muted
      :class="{ mirror: person.local && !screen }"
    />
    <div v-else class="video-placeholder">
      <span>{{ person.name.slice(0, 1) }}</span>
      <p>{{ t("摄像头关闭", "Camera off") }}</p>
    </div>
    <div class="video-caption">
      <span
        >{{ person.name }}{{ person.local ? " · " + t("你", "You") : "" }}</span
      ><span v-if="screen">{{ t("共享画面", "Screen") }}</span
      ><MicOff v-else-if="person.muted" :size="14" />
    </div>
  </article>
</template>
