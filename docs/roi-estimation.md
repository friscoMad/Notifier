# ROI Estimation — Notification Router

This document estimates the cost savings the Notification Router delivers to an engineering organization of 1,600 engineers with an average salary of $150,000/year.

---

## Cost Baseline

| Parameter | Value |
|---|---|
| Engineers | 1,600 |
| Average salary | $150,000/year |
| Hourly cost per engineer | $72/hr (÷ 2,080 work hours/year) |
| Cost per saved minute/engineer/day | $1.20 → **$480K/year** per minute saved daily |

---

## Sources of Savings

The Notification Router eliminates three concrete types of friction:

| Current friction | Without Notifier |
|---|---|
| **Manual CI/CD polling** ("did my checks pass?") | Engineers check manually every 5-10 minutes |
| **Context switches** from irrelevant notifications | Each unnecessary interruption costs ~10-15 min of focus recovery |
| **Slow reaction** to critical events (broken main, failed deploy) | Events detected minutes or hours later, increasing blast radius |

---

## Scenarios

| | **Conservative** | **Medium** | **Optimistic** |
|---|---|---|---|
| **Adoption rate** | 35% (560 eng.) | 60% (960 eng.) | 85% (1,360 eng.) |
| Polling avoidance | 3 min/day | 5 min/day | 8 min/day |
| Context switches avoided | 1 × 8 min = 8 min | 2 × 10 min = 20 min | 3 × 12 min = 36 min |
| **Total min/day/engineer** | **11 min** | **25 min** | **44 min** |
| Hours saved/year | 560 × 46h = **25,600 h** | 960 × 104h = **100,000 h** | 1,360 × 183h = **249,000 h** |
| **Annual savings** | **~$1.8M** | **~$7.2M** | **~$18M** |

*(Based on 250 working days/year)*

---

## Unquantified Benefits

- **Lower MTTR in production**: detecting a failed deploy 20 minutes earlier can prevent hours-long incidents
- **Fewer peer interruptions**: eliminates "hey, did you see if my checks passed?" conversations
- **Better developer experience**: reduced notification fatigue improves focus and retention

---

## Executive Summary

> At 60% adoption saving ~25 min/day per engineer, the system generates **~$7M in annual value** — equivalent to **47 full-time engineers** freed from low-value work.

The conservative estimate ($1.8M) holds even with low adoption and minimal per-engineer savings. The break-even against development cost (hackathon days) is reached within **hours of running in production**.
