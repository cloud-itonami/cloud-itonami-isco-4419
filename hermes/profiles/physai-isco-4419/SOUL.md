# physai-isco-4419 — 他に分類されない一般事務補助員（ISCO 4419）の郵便室・備品室ロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-4419`、ISCO 4419 他に分類されない事務補助員）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: この職種は Wave 0（cognitive substrate、robotics gate なし）。一般事務補助に残る物理的な仕事は郵便室と備品室 ——
受付に届いた荷物を区分棚へ上げ、備品補充の台車を備品室から執務フロアへ運ぶこと。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:parcel-to-pigeonhole-shelf` | manipulator | 受付カウンターの荷物（4 kg）を区分棚へ上げる。動作時間を掃引 | 肩関節ピークトルク `:peak-tau1-nm` | 60 N·m（estimate） |
| `:supply-restock-cart` | transport | 備品補充台車（コピー用紙・トナー・文具）を備品室から執務フロアへ押す（40 m、駆動力 75 N） | 1 区間の所要時間 `:cycle-time-s` | 50 s（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:physai-test`（`test/clericalsupport/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する。repo の test 全 27 本が kbb の runner で走る）。

## 測って分かったこと・限界（成長の第一候補）

1. **区分棚への移載**: 2.5 s で 43.9 N·m、1.2 s で 51.5 N·m、0.8 s で 65.0 N·m、0.5 s で 104.0 N·m。
   限界 60 N·m を守れる最短の動作時間は **0.896 s**。遅くしても約 44 N·m は残る（荷物とアームを支える静的トルク）。
2. **備品台車**: 積荷 10〜60 kg では所要時間 41.62 s で変わらない（巡航 1.0 m/s と加速度上限 0.5 m/s² が効く）。約 100 kg から駆動力 75 N が制約になり（100 kg で 42.18 s、150 kg で 43.28 s）、
   限界 50 s を超えるのは積荷 **255.7 kg**。エネルギーは 10 kg 452 J → 150 kg 1604 J。
3. **estimate のままの値**: 肩トルク上限 60 N·m（5 kg 級協働ロボットの仕様書で置き換える）、区間所要時間 50 s（朝の補充巡回の実測で置き換える）、
   アームの寸法・質量、台車の駆動力 75 N・転がり抵抗係数 0.02。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-4419 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:physai-test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-4419 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
