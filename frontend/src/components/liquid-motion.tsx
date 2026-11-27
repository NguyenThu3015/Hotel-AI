"use client";

import { useEffect } from "react";

/**
 * Computes the lowest commercial display price (>= threshold, default 100,000 VND)
 * so low-denomination E2E/payment test rooms (e.g. 1,000 VND) remain bookable in lists
 * without distorting promotional "Starting from..." badges on Hero & Destination cards.
 */
export function commercialMinPrice(
  values: Array<number | null | undefined>,
  minThreshold = 100_000,
): number | null {
  const valid = values.filter(
    (v): v is number =>
      typeof v === "number" && Number.isFinite(v) && v >= minThreshold,
  );
  if (valid.length === 0) return null;
  return Math.min(...valid);
}

/**
 * Lightweight zero-dependency choreography runtime:
 * 1. Tracks fine-pointer coordinates (--mx, --my) on [data-glass-spotlight] cards via rAF.
 * 2. Progressively reveals [data-reveal] elements via IntersectionObserver while staying
 *    100% visible under SSR, no-JS, or prefers-reduced-motion: reduce.
 */
export function LiquidMotionObserver() {
  useEffect(() => {
    if (typeof window === "undefined") return;

    const reducedMotionQuery = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    );

    const markAllRevealed = () => {
      document.documentElement.classList.remove("lg-motion-ready");
      document
        .querySelectorAll<HTMLElement>("[data-reveal]")
        .forEach((el) => el.setAttribute("data-revealed", "true"));
    };

    if (reducedMotionQuery.matches) {
      markAllRevealed();
      return;
    }

    document.documentElement.classList.add("lg-motion-ready");

    const intersectionObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.setAttribute("data-revealed", "true");
            intersectionObserver.unobserve(entry.target);
          }
        }
      },
      { rootMargin: "0px 0px 140px 0px", threshold: 0.01 },
    );

    const observeNewNodes = () => {
      document
        .querySelectorAll<HTMLElement>('[data-reveal]:not([data-revealed="true"])')
        .forEach((el) => {
          const rect = el.getBoundingClientRect();
          if (rect.top < window.innerHeight + 140 && rect.bottom > -40) {
            el.setAttribute("data-revealed", "true");
          } else {
            intersectionObserver.observe(el);
          }
        });
    };

    observeNewNodes();

    const mutationObserver = new MutationObserver(() => {
      observeNewNodes();
    });
    mutationObserver.observe(document.body, {
      childList: true,
      subtree: true,
    });

    // Pointer spotlight tracking on fine pointers only
    let rafId = 0;
    let lastEvent: PointerEvent | null = null;
    const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)");

    const handlePointerMove = (event: PointerEvent) => {
      lastEvent = event;
      if (rafId) return;
      rafId = window.requestAnimationFrame(() => {
        rafId = 0;
        if (!lastEvent) return;
        const target = (lastEvent.target as HTMLElement | null)?.closest?.(
          "[data-glass-spotlight]",
        ) as HTMLElement | null;
        if (!target) return;
        const rect = target.getBoundingClientRect();
        target.style.setProperty("--mx", `${Math.round(lastEvent.clientX - rect.left)}px`);
        target.style.setProperty("--my", `${Math.round(lastEvent.clientY - rect.top)}px`);
      });
    };

    if (finePointer.matches) {
      window.addEventListener("pointermove", handlePointerMove, { passive: true });
    }

    const handleMotionPrefChange = (e: MediaQueryListEvent) => {
      if (e.matches) markAllRevealed();
    };
    reducedMotionQuery.addEventListener("change", handleMotionPrefChange);

    return () => {
      if (rafId) window.cancelAnimationFrame(rafId);
      intersectionObserver.disconnect();
      mutationObserver.disconnect();
      window.removeEventListener("pointermove", handlePointerMove);
      reducedMotionQuery.removeEventListener("change", handleMotionPrefChange);
    };
  }, []);

  return null;
}
