# Reels Counter

A small Android app that counts how many Instagram reels you scroll and watch.

- A floating bubble shows today's count while you're in Instagram (drag it to move it, tap it to shrink it).
- **Scrolled** = every reel that came onto your screen.
- **Watched** = reels you stayed on for at least 3 seconds (you can change this).
- Reports for today, this week, this month and all time, plus charts for the last 7 and 30 days.
- Optional daily limit: the bubble turns orange, then red, and the phone buzzes.
- Everything stays on the phone.

## How to get the app (no coding needed)

1. Make a free account at github.com.
2. Click **+** (top right) → **New repository**. Name it `reels-counter`, choose **Public**, click **Create repository**.
3. Click **uploading an existing file**. Unzip `ReelsCounter.zip` on your computer, open the `ReelsCounter` folder,
   select everything inside it and drag it onto the page. Click **Commit changes**.
4. Open the **Actions** tab → **set up a workflow yourself**. Delete what's in the editor, paste in everything from
   `github-build-workflow.yml`, then click **Commit changes** twice.
5. Wait about 4 minutes, until the Actions tab shows a green tick.
6. On your phone, open `https://github.com/YOUR-USERNAME/reels-counter/releases/latest/download/ReelsCounter.apk`
   and install it. (On Samsung, you may need to turn off Auto Blocker first and allow your browser to install apps.)
7. Open Reels Counter → **Turn on counter** → Installed apps → Reels Counter → on → Allow.
   If it says "Restricted setting": Settings → Apps → Reels Counter → ⋮ → **Allow restricted settings**, then try again.

## Code

- `ReelsAccessibilityService.java`: detects the Reels viewer and reel swipes, and draws the bubble
- `MainActivity.java`: the stats and settings screen
- `Stats.java`: the database, one row per day
- `ChartView.java`: bar charts
