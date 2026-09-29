package fnl.bots.framework.blackboard;

import fnl.bots.framework.rules.Rules;

/** Mutable {@link EnemyWaveView}, owned by the {@link EnemyWaves} writer. */
public final class EnemyWave implements EnemyWaveView {

    public int shooterId;
    public int decisionTurn;
    public double originX;
    public double originY;
    public double power;
    public double bulletSpeed;
    public double directAngle;
    public int lateralDirection;
    public double maxEscapeAngle;
    public int hitTurn;
    public double hitX;
    public double hitY;
    /** Bullet distance from the origin at the hit minus {@link #radius}; 0 when fact 5 holds exactly. */
    public double hitResidual;

    void reset() {
        shooterId = -1;
        decisionTurn = 0;
        originX = originY = power = bulletSpeed = directAngle = maxEscapeAngle = 0;
        lateralDirection = 1;
        hitTurn = -1;
        hitX = hitY = hitResidual = 0;
    }

    @Override
    public int shooterId() {
        return shooterId;
    }

    @Override
    public int decisionTurn() {
        return decisionTurn;
    }

    @Override
    public double originX() {
        return originX;
    }

    @Override
    public double originY() {
        return originY;
    }

    @Override
    public double power() {
        return power;
    }

    @Override
    public double bulletSpeed() {
        return bulletSpeed;
    }

    @Override
    public double directAngle() {
        return directAngle;
    }

    @Override
    public int lateralDirection() {
        return lateralDirection;
    }

    @Override
    public double maxEscapeAngle() {
        return maxEscapeAngle;
    }

    @Override
    public double radius(int now) {
        return Rules.bulletDistance(decisionTurn, now, bulletSpeed);
    }

    @Override
    public int hitTurn() {
        return hitTurn;
    }

    @Override
    public double hitX() {
        return hitX;
    }

    @Override
    public double hitY() {
        return hitY;
    }
}
