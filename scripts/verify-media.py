# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Two actual RTC clients transmit labelled synthetic camera/screen frames and distinct tones.
No physical camera, microphone or desktop capture is accessed. Not a WAN or device validation.
"""

import asyncio, json, logging, os, time, math, requests
import numpy as np
from livekit import rtc
from quality import ROOT, BASE, ENV, login, request, check, save_state

logging.getLogger("livekit").setLevel(logging.CRITICAL)
STATE = json.loads((ROOT / "private-quality-state.json").read_text())
COLORS = {"alice": (22, 74, 146), "guest": (152, 78, 35), "screen": (24, 93, 80)}
FONT = {
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "S": ["11111", "10000", "10000", "11111", "00001", "00001", "11111"],
    "C": ["11111", "10000", "10000", "10000", "10000", "10000", "11111"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "G": ["11111", "10000", "10000", "10111", "10001", "10001", "11111"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
}


def video_frame(key, screen, tick):
    color = COLORS["screen" if screen else key]
    pixels = np.empty((180, 320, 4), dtype=np.uint8)
    pixels[:, :, :3] = color
    pixels[:, :, 3] = 255
    text = "TEST SCREEN" if screen else "TEST CAMERA"
    for n, char in enumerate(text):
        for y, row in enumerate(FONT.get(char, ["00000"] * 7)):
            for x, bit in enumerate(row):
                if bit == "1":
                    pixels[
                        18 + y * 3 : 21 + y * 3,
                        18 + n * 18 + x * 3 : 21 + n * 18 + x * 3,
                        :3,
                    ] = 235
    # A moving bar proves a continuing encoded stream; sampling uses the untouched center.
    col = (tick * 5) % 260
    pixels[155:164, 20 + col : 40 + col, :3] = 220
    if screen:
        pixels[120:123, 22:298, :3] = 150
        for k in range(3):
            pixels[129 + k * 5 : 132 + k * 5, 22 : 80 + k * 45, :3] = (110, 155, 148)
    return rtc.VideoFrame(320, 180, rtc.VideoBufferType.RGBA, pixels.tobytes())


class Peer:
    def __init__(self, key, frequency, session):
        self.key = key
        self.frequency = frequency
        self.session = session
        self.room = rtc.Room()
        self.audio = rtc.AudioSource(48000, 1)
        self.camera = rtc.VideoSource(320, 180)
        self.screen = rtc.VideoSource(320, 180) if key == "guest" else None
        self.audio_frames = {}
        self.video_frames = {}
        self.color_matches = {}
        self.frequencies = {}
        self.tasks = []
        self.disconnected = asyncio.Event()
        self.sources = []
        self.verifying = False

        @self.room.on("track_subscribed")
        def subscribed(track, pub, participant):
            consumer = (
                self.consume_audio(track, participant.identity)
                if track.kind == rtc.TrackKind.KIND_AUDIO
                else self.consume_video(track, participant.identity, pub.source)
            )
            self.tasks.append(asyncio.create_task(consumer))

        @self.room.on("disconnected")
        def disconnected(reason):
            self.disconnected.set()

    async def connect(self):
        self.lease = request(
            self.session, "/meetings/" + str(STATE["meetingId"]) + "/media", "POST"
        )
        relay = os.getenv("MEETDESK_RELAY_TEST") == "1"
        await self.room.connect(
            BASE.replace("http:", "ws:").replace("https:", "wss:") + "/media",
            self.lease["token"],
            rtc.RoomOptions(
                connect_timeout=12,
                single_peer_connection=False,
                rtc_config=(
                    rtc.RtcConfiguration(
                        ice_transport_type=rtc.IceTransportType.TRANSPORT_RELAY
                    )
                    if relay
                    else None
                ),
            ),
        )
        self.audio_track = rtc.LocalAudioTrack.create_audio_track(
            "TEST synthetic tone", self.audio
        )
        await self.room.local_participant.publish_track(
            self.audio_track,
            rtc.TrackPublishOptions(source=rtc.TrackSource.SOURCE_MICROPHONE),
        )
        self.camera_track = rtc.LocalVideoTrack.create_video_track(
            "TEST synthetic camera", self.camera
        )
        await self.room.local_participant.publish_track(
            self.camera_track,
            rtc.TrackPublishOptions(
                source=rtc.TrackSource.SOURCE_CAMERA,
                video_encoding=rtc.VideoEncoding(max_framerate=6, max_bitrate=300000),
            ),
        )
        if self.screen:
            self.screen_track = rtc.LocalVideoTrack.create_video_track(
                "TEST synthetic screen", self.screen
            )
            await self.room.local_participant.publish_track(
                self.screen_track,
                rtc.TrackPublishOptions(
                    source=rtc.TrackSource.SOURCE_SCREENSHARE,
                    video_encoding=rtc.VideoEncoding(
                        max_framerate=6, max_bitrate=300000
                    ),
                ),
            )
        print("TEST RTC peer connected with actual media: " + self.key, flush=True)

    async def consume_audio(self, track, identity):
        stream = rtc.AudioStream(track, sample_rate=48000, num_channels=1)
        try:
            async for e in stream:
                if not self.verifying:
                    continue
                samples = np.frombuffer(e.frame.data, dtype=np.int16).astype(np.float64)
                if np.sqrt(np.mean(samples * samples)) < 120:
                    continue
                n = self.audio_frames.get(identity, 0) + 1
                self.audio_frames[identity] = n
                if n % 50 == 0 or n <= 30:
                    spectrum = np.abs(np.fft.rfft(samples * np.hanning(len(samples))))
                    hz = float(
                        np.fft.rfftfreq(len(samples), 1 / 48000)[
                            int(np.argmax(spectrum))
                        ]
                    )
                    self.frequencies.setdefault(identity, []).append(hz)
        finally:
            await stream.aclose()

    async def consume_video(self, track, identity, source):
        key = identity + ":" + str(source)
        stream = rtc.VideoStream(track, format=rtc.VideoBufferType.RGBA)
        try:
            async for e in stream:
                if not self.verifying:
                    continue
                f = e.frame
                pixels = np.frombuffer(f.data, dtype=np.uint8).reshape(
                    f.height, f.width, 4
                )
                region = pixels[
                    f.height // 3 : 2 * f.height // 3,
                    f.width // 3 : 2 * f.width // 3,
                    :3,
                ].mean(axis=(0, 1))
                expected = COLORS[
                    (
                        "screen"
                        if source == rtc.TrackSource.SOURCE_SCREENSHARE
                        else ("guest" if self.key == "alice" else "alice")
                    )
                ]
                self.video_frames[key] = self.video_frames.get(key, 0) + 1
                if np.max(np.abs(region - np.array(expected))) < 24:
                    self.color_matches[key] = self.color_matches.get(key, 0) + 1
        finally:
            await stream.aclose()

    async def publish(self, seconds):
        async def tones():
            for k in range(math.ceil(seconds * 50)):
                x = np.arange(960) + k * 960
                pcm = (np.sin(2 * np.pi * self.frequency * x / 48000) * 1800).astype(
                    np.int16
                )
                await self.audio.capture_frame(
                    rtc.AudioFrame(pcm.tobytes(), 48000, 1, 960)
                )
            await self.audio.wait_for_playout()

        async def pictures():
            start = time.monotonic()
            for k in range(math.ceil(seconds * 6)):
                self.camera.capture_frame(video_frame(self.key, False, k))
                if self.screen:
                    self.screen.capture_frame(video_frame(self.key, True, k))
                await asyncio.sleep(max(0, start + (k + 1) / 6 - time.monotonic()))

        await asyncio.gather(tones(), pictures())

    async def close(self):
        await self.room.disconnect()
        for t in self.tasks:
            t.cancel()
        await asyncio.gather(*self.tasks, return_exceptions=True)
        await self.audio.aclose()
        await self.camera.aclose()
        if self.screen:
            await self.screen.aclose()


def sessions():
    a = login(STATE["users"]["alice"]["username"], STATE["password"])
    b = requests.Session()
    b.cookies.update(STATE["guest"]["cookies"])
    return a, b


async def denied_probe(lease):
    probe = rtc.Room()
    denied = False
    try:
        await probe.connect(
            BASE.replace("http:", "ws:").replace("https:", "wss:") + "/media",
            lease["token"],
            rtc.RoomOptions(connect_timeout=4, single_peer_connection=False),
        )
    except Exception:
        denied = True
    finally:
        await probe.disconnect()
    check(denied, "old signed token cannot reconnect through access gateway")


def sample_result(a, b, seconds):
    result = {
        "seconds": seconds,
        "forcedRelay": os.getenv("MEETDESK_RELAY_TEST") == "1",
        "syntheticMedia": True,
    }
    for r, s in [(a, b), (b, a)]:
        identity = s.lease["identity"]
        audio = r.audio_frames.get(identity, 0)
        check(audio >= 30, "actual received audio frames")
        measured = float(np.median(r.frequencies.get(identity, [])))
        check(
            abs(measured - s.frequency) < 60,
            "remote tone matches distinct source frequency",
        )
        sources = [rtc.TrackSource.SOURCE_CAMERA] + (
            [rtc.TrackSource.SOURCE_SCREENSHARE] if s.key == "guest" else []
        )
        frames = {}
        for source in sources:
            k = identity + ":" + str(source)
            n = r.video_frames.get(k, 0)
            good = r.color_matches.get(k, 0)
            check(
                n >= max(10, seconds * 4),
                "continuing actual decoded remote video frames",
            )
            check(good / n > 0.95, "decoded frames match remote source content")
            frames[
                "screen" if source == rtc.TrackSource.SOURCE_SCREENSHARE else "camera"
            ] = {"decoded": n, "correctContent": good}
        result[r.key] = {"audioFrames": audio, "toneHz": measured, "video": frames}
    return result


async def main():
    seconds = int(os.getenv("MEETDESK_MEDIA_SECONDS", "8"))
    sa, sb = sessions()
    a, b = Peer("alice", 440, sa), Peer("guest", 880, sb)
    admin = login(ENV["ADMIN_USERNAME"], ENV["ADMIN_PASSWORD"])
    p = "/meetings/" + str(STATE["meetingId"])
    try:
        await a.connect()
        await b.connect()
        publishers = [
            asyncio.create_task(a.publish(seconds + 5)),
            asyncio.create_task(b.publish(seconds + 5)),
        ]
        # SDK tracks exist before any source frame is submitted. Measure only the
        # actual producer window, after two seconds of source/decoder warm-up.
        await asyncio.sleep(2)
        a.verifying = b.verifying = True
        start = time.monotonic()
        last_a = 0
        last_b = 0
        while time.monotonic() - start < seconds:
            await asyncio.sleep(min(30, max(0.1, seconds - (time.monotonic() - start))))
            check(
                not a.disconnected.is_set() and not b.disconnected.is_set(),
                "no unintended RTC disconnection during acceptance",
            )
            aa = sum(a.audio_frames.values())
            bb = sum(b.audio_frames.values())
            check(
                aa > last_a and bb > last_b,
                "both peers continue receiving actual audio",
            )
            last_a, last_b = aa, bb
            request(sa, p)
            request(sb, p)
            # The observer is a third authenticated session. Keep its REST
            # activity alive too; media activity does not refresh HTTP sessions.
            request(admin, p + "/attendance")
            print(
                json.dumps(
                    {
                        "elapsedSeconds": round(time.monotonic() - start),
                        "audioFramesA": aa,
                        "audioFramesB": bb,
                        "videoFramesA": sum(a.video_frames.values()),
                        "videoFramesB": sum(b.video_frames.values()),
                    }
                ),
                flush=True,
            )
        await asyncio.gather(*publishers)
        await asyncio.sleep(1)
        result = sample_result(a, b, seconds)
        if seconds <= 20:
            aid = STATE["guest"]["id"]
            request(
                admin,
                p + "/attendees/" + str(aid),
                "POST",
                {"action": "SPEAK", "enabled": False},
            )
            for _ in range(50):
                if (
                    b.room.local_participant.permissions
                    and not b.room.local_participant.permissions.can_publish
                ):
                    break
                await asyncio.sleep(0.1)
            check(
                b.room.local_participant.permissions is not None
                and not b.room.local_participant.permissions.can_publish,
                "actual SFU view-only permission applied",
            )
            await denied_probe(b.lease)
            request(
                admin,
                p + "/attendees/" + str(aid),
                "POST",
                {"action": "SPEAK", "enabled": True},
            )
            await asyncio.sleep(2)
            # Fresh unexpired token allows attribution of the following denial to removal, not expiry.
            fresh = request(sb, p + "/media", "POST")
            replacement = rtc.Room()
            removed = asyncio.Event()

            @replacement.on("disconnected")
            def current_disconnected(reason):
                removed.set()

            try:
                await replacement.connect(
                    BASE.replace("http:", "ws:").replace("https:", "wss:") + "/media",
                    fresh["token"],
                    rtc.RoomOptions(connect_timeout=10, single_peer_connection=False),
                )
                request(
                    admin, p + "/attendees/" + str(aid), "POST", {"action": "REMOVE"}
                )
                await asyncio.wait_for(removed.wait(), timeout=10)
                await denied_probe(fresh)
            finally:
                await replacement.disconnect()
            result["viewOnlyApplied"] = True
            result["removedAndUnexpiredTokenDenied"] = True
        result["observedAttendance"] = len(
            [x for x in request(admin, p + "/attendance") if x["connectedAt"]]
        )
        check(
            result["observedAttendance"] >= 2,
            "media observations create actual connection records",
        )
        name = (
            "private-relay-result.json"
            if os.getenv("MEETDESK_RELAY_TEST") == "1"
            else "private-media-result.json"
        )
        if seconds >= 1800:
            name = "private-30minute-result.json"
        save_state(name, result)
        print(json.dumps(result), flush=True)
    finally:
        await a.close()
        await b.close()


if __name__ == "__main__":
    asyncio.run(main())
    import gc

    gc.collect()
