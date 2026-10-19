using Robocode.TankRoyale.BotApi;
using Robocode.TankRoyale.BotApi.Events;

public class StarterBot : Bot
{
    public static void Main(string[] args)
    {
        if (Environment.GetEnvironmentVariable("RUMBLE_SMOKE") == "1")
        {
            Console.WriteLine("RUMBLE_SMOKE_READY StarterBot");
            return;
        }
        new StarterBot().Start();
    }

    public override void Run()
    {
        while (IsRunning)
        {
            TurnGunRight(10);
        }
    }

    public override void OnScannedBot(ScannedBotEvent evt)
    {
        Fire(1);
    }
}
