package org.firstinspires.ftc.teamcode.commands.base;

import com.acmerobotics.dashboard.telemetry.TelemetryPacket;

import java.util.ArrayList;
import java.util.List;

public class SequentialCommandGroup extends CommandBase {
    private final List<Command> commands;
    public long timeout = 0; // 整个组的超时时间
    private int currentCommandIndex = -1;
    private Command currentCommand = null;
    private long groupStartTime;
    private long currentCommandStartTime;
    private boolean isFinished = false;

    public SequentialCommandGroup(Command... commands) {
        this.commands = new ArrayList<>();
        addCommands(commands);
    }

    // 添加这个方法来支持添加多个命令
    public void addCommands(Command... commands) {
        for (Command cmd : commands) {
            this.commands.add(cmd);
            // 合并所有子命令的requirements
            cmd.getRequirements().forEach(this::addRequirement);
        }
    }

    @Override
    public void initialize() {
        currentCommandIndex = 0;
        isFinished = false;
        groupStartTime = System.currentTimeMillis();

        if (!commands.isEmpty()) {
            currentCommand = commands.get(0);
            currentCommandStartTime = System.currentTimeMillis();
            currentCommand.initialize();
        }
    }

    @Override
    public void execute(TelemetryPacket packet) {
        if (isFinished || commands.isEmpty()) {
            return;
        }

        // 检查整个组的超时
        if (timeout > 0 && (System.currentTimeMillis() - groupStartTime) >= timeout) {
            cancelCurrentCommand(true);
            isFinished = true;
            return;
        }

        // 执行当前命令
        currentCommand.execute(packet);

        // 检查当前命令是否完成
        if (currentCommand.isFinished()) {
            currentCommand.end(false);
            moveToNextCommand();
        }
        // 检查当前命令的超时
        else if (currentCommand.getTimeout() > 0 &&
                (System.currentTimeMillis() - currentCommandStartTime) >= currentCommand.getTimeout()) {
            cancelCurrentCommand(true);
            moveToNextCommand();
        }
    }

    private void moveToNextCommand() {
        currentCommandIndex++;
        if (currentCommandIndex < commands.size()) {
            currentCommand = commands.get(currentCommandIndex);
            currentCommandStartTime = System.currentTimeMillis();
            currentCommand.initialize();
        } else {
            isFinished = true;
            currentCommand = null;
        }
    }

    private void cancelCurrentCommand(boolean interrupted) {
        if (currentCommand != null) {
            currentCommand.end(interrupted);
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished || commands.isEmpty();
    }

    @Override
    public void end(boolean interrupted) {
        if (interrupted && currentCommand != null) {
            cancelCurrentCommand(true);
        }
        currentCommand = null;
    }

    @Override
    public long getTimeout() {
        return timeout;
    }
}