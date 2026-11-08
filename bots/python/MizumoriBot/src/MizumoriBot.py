# The RUMBLE_SMOKE entry-point pattern is adapted from the official Python
# StarterBot (Apache-2.0). Modified: config validation and bot name.
# See THIRD_PARTY_NOTICES.txt and LICENSE-APACHE-2.0.txt.
"""MizumoriBot: rule-based duelist for Tank Royale Python Bot API 1.4.0."""
from __future__ import annotations

import math
import os
from pathlib import Path
from Defense import Defense
from GatedAim import GatedAim as ShotAim

from robocode_tank_royale.bot_api.bot import Bot
from robocode_tank_royale.bot_api.bot_info import BotInfo
from robocode_tank_royale.bot_api.color import Color


def relative(angle: float) -> float:
    return (angle + 180) % 360 - 180


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def startup_motion(x: float, y: float, direction: float,
                   width: int, height: int) -> tuple[float, float]:
    """Face the interior before accelerating from a start close to a wall.

    The normal wall smoother chooses a future travel heading, but the body
    needs several turns to face it. At rest we can turn without drifting into
    a wall, then leave at speed 4. Backwards travel is allowed as usual.
    """
    safe_x = clamp(x, 95, width - 95)
    safe_y = clamp(y, 95, height - 95)
    desired = math.degrees(math.atan2(safe_y - y, safe_x - x))
    error = relative(desired - direction)
    drive = 1
    if abs(error) > 90:
        error = relative(error + 180)
        drive = -1
    return error, 0 if abs(error) > 15 else drive * 4


def startup_risk(x: float, y: float, direction: float, speed: float,
                 error: float, target_speed: float, width: int, height: int) -> bool:
    """Check the next six turns of the existing steering before overriding it.

    Preserve safe normal starts. Predict acceleration and the speed-dependent
    turn limit; moving uses the current heading before the next body turn.
    The check is repeated during the launch window as the target changes.
    """
    target_heading = direction + error
    drive = 1 if target_speed >= 0 else -1
    for _ in range(6):
        error = relative(target_heading - direction)
        target = drive * (5 if abs(error) > 45 else 8)
        if speed * target < 0:
            if abs(speed) >= 2:
                speed -= math.copysign(2, speed)
            else:
                speed = math.copysign(1 - abs(speed) / 2, target)
        elif abs(target) > abs(speed):
            speed += math.copysign(min(1, abs(target - speed)), target)
        else:
            speed += clamp(target - speed, -2, 2)
        x += math.cos(math.radians(direction)) * speed
        y += math.sin(math.radians(direction)) * speed
        if not (18.05 < x < width - 18.05 and 18.05 < y < height - 18.05):
            return True
        limit = 10 - .75 * abs(speed)
        direction += clamp(error, -limit, limit)
    return False


def intercept(enemy: dict, origin: tuple[float, float], arena: tuple[int, int],
              bullet_speed: float, delay: int) -> tuple[float, float]:
    """Discrete circular prediction; uses only previously scanned information.

    delay covers scan age and the command's next turn. Clamp at the physical
    wall rather than predicting a target outside the arena.
    """
    x, y = enemy["x"], enemy["y"]
    heading = math.radians(enemy["direction"])
    rate = math.radians(clamp(enemy["rate"], -10, 10))
    for tick in range(1, 121):
        x = clamp(x + math.cos(heading) * enemy["speed"], 18, arena[0] - 18)
        y = clamp(y + math.sin(heading) * enemy["speed"], 18, arena[1] - 18)
        heading += rate
        if tick > delay and (tick - delay) * bullet_speed >= math.hypot(x - origin[0], y - origin[1]):
            break
    return x, y


class MizumoriBot(Bot):
    def __init__(self) -> None:
        # Absolute config path also permits launching from another working directory.
        super().__init__(BotInfo.from_file(str(Path(__file__).resolve().parents[1] / "MizumoriBot.json")))
        self._defense = Defense('safe')
        self._aim_engine = ShotAim('gated')
        self.enemy = None
        self.orbit = 1
        self.last_reverse = -100
        self.escape_until = 0
        self._startup_guard = False
        self._startup_watch = False
        self.stats = {}

    def on_game_started(self, event) -> None:
        # Keep learning within a game, isolate separate games/opponents.
        self._defense = Defense('safe')
        self._aim_engine = ShotAim('gated')

    def run(self) -> None:
        self._defense.new_round()
        self._aim_engine.new_round()
        self.enemy = None
        self.orbit = 1 if self.round_number % 2 else -1
        self.last_reverse = -100
        self.escape_until = 0
        self._startup_guard = False
        self._startup_watch = min(self.x, self.y, self.arena_width - self.x,
                                  self.arena_height - self.y) < 65
        self.stats = dict(scans=0, shots=0, hits=0, received=0, walls=0,
                          collisions=0, skipped=0)
        self.adjust_gun_for_body_turn = True
        self.adjust_radar_for_body_turn = True
        self.adjust_radar_for_gun_turn = True
        self.body_color = Color.from_rgb(40, 110, 125)
        self.turret_color = Color.from_rgb(95, 170, 155)
        self.radar_color = Color.from_rgb(185, 225, 210)
        self.bullet_color = Color.from_rgb(100, 235, 220)
        self.set_fire_assist(False)
        while self.running:
            self._step()
            self.go()

    def _step(self) -> None:
        self._defense.update(self)
        defensive_speed = self._defense.control(self)
        enemy = self.enemy
        fresh = enemy is not None and self.turn_number - enemy["turn"] <= 12
        if fresh:
            distance = math.hypot(enemy["x"] - self.x, enemy["y"] - self.y)
            bearing = self.direction_to(enemy["x"], enemy["y"])
            radar_error = relative(bearing - self.radar_direction)
            # Overshoot the target each turn so it stays inside the radar sweep.
            self.radar_turn_rate = clamp(2 * radar_error + (4 if radar_error >= 0 else -4), -45, 45)
            desired = bearing + self.orbit * (90 + clamp((270 - distance) * .16, -35, 35))
            self._aim(enemy, distance)
        else:
            self.radar_turn_rate = 45
            self.gun_turn_rate = 15
            desired = self.direction_to(self.arena_width / 2, self.arena_height / 2) + 50

        if self.turn_number < self.escape_until:
            desired = self.direction_to(self.arena_width / 2, self.arena_height / 2)
        # Wall smoothing: look ahead along actual travel direction before steering.
        for _ in range(72):
            a = math.radians(desired)
            px, py = self.x + 145 * math.cos(a), self.y + 145 * math.sin(a)
            if 45 < px < self.arena_width - 45 and 45 < py < self.arena_height - 45:
                break
            desired += self.orbit * 5
        error = relative(desired - self.direction)
        drive = 1
        if abs(error) > 90:
            error = relative(error + 180)
            drive = -1
        # Slow during a sharp turn, keeping enough steering authority near walls.
        requested_speed = drive * (5 if abs(error) > 45 else 8)
        if self._startup_watch:
            margin = min(self.x, self.y, self.arena_width - self.x,
                         self.arena_height - self.y)
            if margin >= 85:
                self._startup_watch = self._startup_guard = False
            elif self.turn_number <= 30 and not self._startup_guard:
                self._startup_guard = startup_risk(
                    self.x, self.y, self.direction, self.speed, error,
                    requested_speed, self.arena_width, self.arena_height)
            if self._startup_guard:
                self.turn_rate, self.target_speed = startup_motion(
                    self.x, self.y, self.direction, self.arena_width, self.arena_height)
                return
            if self.turn_number > 30:
                self._startup_watch = False
        self.turn_rate = error
        self.target_speed = requested_speed if defensive_speed is None else defensive_speed

    def _aim(self, enemy: dict, distance: float) -> None:
        power = 2.5 if distance < 330 else 1.7
        if distance < 140:
            power = 3.0
        if self.energy < 18:
            power = min(power, 1.0)
        # Avoid spending a full-power bullet to finish a nearly defeated opponent.
        power = min(power, max(.1, (enemy["energy"] + 2) / 6))
        power = min(power, max(0, self.energy - .5))
        speed = self.calc_bullet_speed(power)
        px, py = intercept(enemy, (self.x, self.y),
                           (self.arena_width, self.arena_height), speed,
                           max(0, self.turn_number - enemy["turn"]) + 1)
        (px, py), selection = self._aim_engine.plan(enemy, (self.x,self.y),
            (self.arena_width,self.arena_height), power, self.turn_number, (px,py))
        self._aim_diagnostic = dict(predictedTarget=[px,py],firepowerDecision=power,**selection)
        error = relative(self.direction_to(px, py) - self.gun_direction)
        self.gun_turn_rate = error
        tolerance = math.degrees(math.atan2(12, max(distance, 1)))
        # Keep tracking normally while waiting for a public energy advantage.
        # Avoid feeding an evasive opponent continuous, low-return waves.
        # Budget only a small positive lead; do not stay passive while losing.
        # A persistently stationary target remains a normal firing opportunity.
        lead = self.energy - enemy["energy"]
        budget_band = 0 <= lead < power + 8
        energy_ready = distance < 250 or enemy.get("stationary", 0) >= 12 or not budget_band
        self._fire_control_diagnostic = dict(energyReady=energy_ready)
        if energy_ready and self.gun_heat == 0 and abs(error) < tolerance and power >= .1:
            self._aim_engine.requested(self.turn_number)
            self.set_fire(power)

    def _reverse(self) -> None:
        if self.turn_number - self.last_reverse >= 20:
            self.orbit *= -1
            self.last_reverse = self.turn_number

    def on_scanned_bot(self, e) -> None:
        old = self.enemy
        rate = 0.0
        if old and old["id"] == e.scanned_bot_id and e.turn_number > old["turn"]:
            rate = relative(e.direction - old["direction"]) / (e.turn_number - old["turn"])
        stationary = 0
        if old and old["id"] == e.scanned_bot_id and 0 < e.turn_number-old["turn"] <= 2 and abs(e.speed)<1 and abs(old["speed"])<1:
            stationary = old.get("stationary",0) + e.turn_number-old["turn"]
        self.enemy = dict(stationary=stationary,id=e.scanned_bot_id, x=e.x, y=e.y, direction=e.direction,
                          speed=e.speed, energy=e.energy, turn=e.turn_number, rate=rate)
        self.stats["scans"] += 1
        self._defense.observe(self.enemy)
        self._aim_engine.observe(self.enemy)

    def on_hit_by_bullet(self, e) -> None:
        self._defense.gain(e.turn_number, e.bullet.power)
        self._defense.hit(self, e)
        self.stats["received"] += 1
        self._reverse()

    def on_hit_wall(self, e) -> None:
        self.stats["walls"] += 1
        self._reverse()
        self.escape_until = self.turn_number + 25

    def on_hit_bot(self, e) -> None:
        self._defense.collision(e.turn_number)
        self.stats["collisions"] += 1
        self._reverse()
        self.escape_until = self.turn_number + 15

    def on_bot_death(self, e) -> None:
        if self.enemy and e.victim_id == self.enemy["id"]:
            self.enemy = None

    def on_bullet_fired(self, e) -> None:
        self.stats["shots"] += 1
        self._aim_engine.fired(e)

    def on_bullet_hit(self, e) -> None:
        self._defense.damage(e.turn_number, e.damage)
        self.stats["hits"] += 1

    def on_skipped_turn(self, e) -> None:
        self.stats["skipped"] += 1



def main() -> None:
    if os.environ.get("RUMBLE_SMOKE") == "1":
        BotInfo.from_file(str(Path(__file__).resolve().parents[1] / "MizumoriBot.json"))
        print("RUMBLE_SMOKE_READY MizumoriBot")
        return
    MizumoriBot().start()


if __name__ == "__main__":
    main()
