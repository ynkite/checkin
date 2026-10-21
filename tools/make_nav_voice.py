"""길 안내 목소리 파일 만들기 — edge-tts(ko-KR-SunHiNeural) 로 한 번 돌려 둔다.

    python make_nav_voice.py [장소 이름 ...]

결과: ../src/main/resources/static/audio/nav/*.mp3 + index.json (문장 -> 파일 이름).
nav-voice.js 가 index.json 을 읽어, 문장이 있으면 파일을 틀고 없으면 기기 목소리를 쓴다.
실행 중인 앱은 이 도구를 부르지 않는다. 파일만 쓴다.

장소 이름을 주면 그 이름이 든 문장(제안 카드·도착)도 미리 만든다 — 시연 여행용.
이미 있는 파일은 건너뛴다. edge-tts 는 저장소 바깥 가상환경에 깐다 (pip install edge-tts).
"""
import asyncio, hashlib, json, sys
from pathlib import Path

import edge_tts

VOICE = 'ko-KR-SunHiNeural'
OUT = Path(__file__).resolve().parent.parent / 'src/main/resources/static/audio/nav'

DIST = ['500미터 앞', '300미터 앞', '100미터 앞', '잠시 후']
KIND = ['직진', '좌회전', '우회전', '10시 방향 좌회전', '2시 방향 우회전', '급좌회전', '급우회전',
        '유턴', '회전교차로 진입', '진출', '진입', '목적지']
LANE = ['왼쪽 차선으로 가세요', '오른쪽 차선으로 가세요', '미리 왼쪽 차선으로 가세요', '미리 오른쪽 차선으로 가세요']
FULL = [
    '운전 중에는 화면을 보지 마세요. 소리로 안내합니다.',
    '안내를 시작합니다.',
    '경로를 벗어나 다시 안내합니다.',
    '도착했습니다.',
    '네, 말씀하세요.',
    '다른 곳을 찾아볼게요.',
    '순서를 바꿀지 보여 드릴게요.',
    '그대로 갑니다.',
    '다음 장소로 안내할까요?',
    '곧 어린이보호구역입니다. 속도를 줄이세요.',
    '곧 과속방지턱이 있습니다. 속도를 줄이세요.',
    '곧 단속카메라입니다.',
    '곧 터널입니다. 전조등을 켜세요.',
]
PLACE = [
    '{}에 사람이 많습니다. 다른 곳으로 갈까요, 그대로 갈까요?',
    '{}에 비 소식이 있습니다. 실내로 바꾸거나 순서를 미룰 수 있습니다.',
    '{}에 도착했습니다.',
]


def name_of(text):
    return hashlib.sha1(text.encode('utf-8')).hexdigest()[:10] + '.mp3'


async def make(text, path):
    await edge_tts.Communicate(text, VOICE, rate='+5%').save(str(path))


async def main(places):
    OUT.mkdir(parents=True, exist_ok=True)
    texts = DIST + KIND + LANE + FULL + [p.format(n) for n in places for p in PLACE]
    idx = {}
    if (OUT / 'index.json').exists():
        idx = json.loads((OUT / 'index.json').read_text(encoding='utf-8'))
    for t in texts:
        f = name_of(t)
        if not (OUT / f).exists():
            await make(t, OUT / f)
            print('만듦', f, t)
        idx[t] = f
    (OUT / 'index.json').write_text(json.dumps(idx, ensure_ascii=False, indent=1), encoding='utf-8')
    print(len(idx), '문장')


if __name__ == '__main__':
    asyncio.run(main(sys.argv[1:]))
