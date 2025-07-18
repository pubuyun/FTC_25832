package org.firstinspires.ftc.teamcode.opmodes.auto.sample;

import static org.firstinspires.ftc.teamcode.opmodes.auto.AutoPaths.PICKUP1;
import static org.firstinspires.ftc.teamcode.opmodes.auto.AutoPaths.PICKUP2;
import static org.firstinspires.ftc.teamcode.opmodes.auto.AutoPaths.PICKUP3;
import static org.firstinspires.ftc.teamcode.opmodes.auto.AutoPaths.RobotPosition;
import static org.firstinspires.ftc.teamcode.opmodes.auto.AutoPaths.SCORE;
import static org.firstinspires.ftc.teamcode.opmodes.auto.AutoPaths.START;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.roadrunner.Action;
import com.acmerobotics.roadrunner.ParallelAction;
import com.acmerobotics.roadrunner.Pose2d;
import com.acmerobotics.roadrunner.SequentialAction;
import com.acmerobotics.roadrunner.Vector2d;
import com.acmerobotics.roadrunner.ftc.Actions;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.firstinspires.ftc.teamcode.commands.base.WaitCommand;
import org.firstinspires.ftc.teamcode.commands.slide.LowerSlideCommands;
import org.firstinspires.ftc.teamcode.commands.slide.LowerSlideGrabSequenceCommand;
import org.firstinspires.ftc.teamcode.commands.slide.LowerSlideUpdatePID;
import org.firstinspires.ftc.teamcode.commands.slide.LowerUpperTransferSequenceCommand;
import org.firstinspires.ftc.teamcode.commands.slide.UpperSlideCommands;
import org.firstinspires.ftc.teamcode.commands.slide.UpperSlideUpdatePID;
import org.firstinspires.ftc.teamcode.commands.vision.AngleAdjustCommand;
import org.firstinspires.ftc.teamcode.commands.vision.CameraUpdateDetectorResult;
import org.firstinspires.ftc.teamcode.commands.vision.DistanceAdjustLUTThetaR;
import org.firstinspires.ftc.teamcode.opmodes.auto.AutoPaths;
import org.firstinspires.ftc.teamcode.roadrunner.MecanumDrive;
import org.firstinspires.ftc.teamcode.sensors.limelight.LimeLightImageTools;
import org.firstinspires.ftc.teamcode.sensors.limelight.Limelight;
import org.firstinspires.ftc.teamcode.subsystems.slides.LowerSlide;
import org.firstinspires.ftc.teamcode.subsystems.slides.UpperSlide;
import org.firstinspires.ftc.teamcode.utils.RobotStateStore;
import org.firstinspires.ftc.teamcode.utils.control.ConfigVariables;

@Autonomous
public final class AutoSample05 extends LinearOpMode {
    private MecanumDrive drive;

    private LowerSlide lowSlide;
    private LowerSlideCommands lowerSlideCommands;
    private UpperSlide upSlide;
    private UpperSlideCommands upperSlideCommands;

    private Limelight camera;

    private Action waitSeconds(Pose2d pose, double seconds) {
        return drive.actionBuilder(pose)
                .waitSeconds(seconds)
                .build();
    }

    // --- Helper for repeated drop/reset sequence ---
    private SequentialAction dropAndResetUpperSlides() {
        return new SequentialAction(
                upperSlideCommands.openClaw(), // drop
                // SCORED
                new WaitCommand(ConfigVariables.AutoTesting.B_AFTERSCOREDELAY_S).toAction(),
                new ParallelAction(
                        // upperSlideCommands.closeExtendoClaw(),
                        upperSlideCommands.scorespec()
                        // score spec position for upperslides to go down
                ),
                upperSlideCommands.slidePos0());
    }

    // --- Helper for repeated front-for-drop sequence ---
    private SequentialAction frontForDrop() {
        return new SequentialAction(
                upperSlideCommands.front(),
                new WaitCommand(ConfigVariables.AutoTesting.A_DROPDELAY_S).toAction());
    }

    // --- Helper for repeated drive-to-score and prep upper slides ---
    private ParallelAction driveToScoreAndPrepUpperSlides(RobotPosition startPOS) {
        return new ParallelAction(
                // Drive to score
                drive.actionBuilder(startPOS.pose)
                        .strafeToSplineHeading(SCORE.pos, SCORE.heading)
                        .build(),
                upperSlideCommands.closeClaw(),
                upperSlideCommands.front(),
                upperSlideCommands.slidePos3() // need scorespec or transfer pos to go up safely
        );
    }

    // --- Helper for repeated transfer-while-driving sequence ---
    private SequentialAction transferWhileDriving() {
        return new SequentialAction(
                transferSequence(),
                // upperSlideCommands.closeClaw(),
                new WaitCommand(ConfigVariables.AutoTesting.X_TRANSFERWHILEDRIVEAFTERTRANSFERDELAY_S)
                        .toAction(),
                upperSlideCommands.inter(),
                upperSlideCommands.slidePos3() // need scorespec or inter or transfer pos to go up
                // safely
        );
    }

    private SequentialAction scoreSequence(RobotPosition startPOS, double lowerslideExtendLength) {
        return new SequentialAction(
                driveToScoreAndPrepUpperSlides(startPOS),
                // front pos for drop
                // new WaitCommand(ConfigVariables.AutoTesting.A_DROPDELAY_S).toAction(),
                // upperSlideCommands.openExtendoClaw(),
                // new WaitCommand(ConfigVariables.AutoTesting.W_AFTEREXTENDOOPEN_S).toAction(),
                dropAndResetUpperSlides());
    }

    private SequentialAction transferAndScoreSequence(RobotPosition startPOS, AutoPaths.RobotPosition pickupPos,
                                                      double lowerslideExtendLength) {
        return new SequentialAction(
                new ParallelAction(
                        drive.actionBuilder(startPOS.pose)
                                .strafeToSplineHeading(SCORE.pos, SCORE.heading)
                                .build(),
                        transferWhileDriving()),
                // front pos for drop
                // upperSlideCommands.openExtendoClaw(),
                // new WaitCommand(ConfigVariables.AutoTesting.W_AFTEREXTENDOOPEN_S).toAction(),
                frontForDrop(),
                dropAndResetUpperSlides());
    }

    private SequentialAction pickupAndScoreSequence(RobotPosition startPOS, AutoPaths.RobotPosition pickupPos,
                                                    double lowerslideExtendLength) {
        return new SequentialAction(
                // Drive to pickup
                // new SetDriveSpeedCommand(40).toAction(),

                new ParallelAction(
                        drive.actionBuilder(startPOS.pose)
                                .strafeToSplineHeading(pickupPos.pos, pickupPos.heading)
                                .build(),
                        lowerSlideCommands.setSlidePos(lowerslideExtendLength)),

                new WaitCommand(ConfigVariables.AutoTesting.Y_PICKUPDELAY).toAction(),
                adjustSequence(),
                pickupSequence(),
                // waitSeconds(pickupPos.pose, ConfigVariables.AutoTesting.C_AFTERGRABDELAY_S),

                // transferSequence(pickupPos),

                // Score
                // waitSeconds(pickupPos.pose,
                // ConfigVariables.AutoTesting.H_TRANSFERCOMPLETEAFTERDELAY_S),
                transferAndScoreSequence(pickupPos, pickupPos, lowerslideExtendLength));
    }

    private SequentialAction tankPickupAndScoreSequence() {
        return new SequentialAction(
                new ParallelAction(
                        drive.actionBuilder(SCORE.pose)
                                .strafeTo(new Vector2d(
                                        39, 28))
                                .splineTo(new Vector2d(
                                                30, 15),
                                        Math.toRadians(205))
                                .build(),
                        lowerSlideCommands.slidePos1()
                ),
                new WaitCommand(ConfigVariables.AutoTesting.I_SUBDELAY_S)
                        .toAction(),
                adjustSequence(),
                new WaitCommand(ConfigVariables.Camera.CAMERA_DELAY).toAction(),
                new AngleAdjustCommand(lowSlide, camera)
                        .toAction(),
                pickupSequence(),
                new ParallelAction(
                        // Drive to score
                        drive.actionBuilder(SCORE.pose, ConfigVariables.AutoTesting.TANK_SPEED_MUTIPLIER, ConfigVariables.AutoTesting.TANK_ANGULAR_MUTIPLIER)
                                .setReversed(true)
                                .splineTo(new Vector2d(
                                                44, 28),
                                        SCORE.heading - Math
                                                .toRadians(170))
                                .splineTo(SCORE.pos,
                                        SCORE.heading - Math
                                                .toRadians(170))
                                .build(),
                        transferWhileDriving()),
                frontForDrop(),
                dropAndResetUpperSlides());
    }

    private Action transferSequence() {
        return new LowerUpperTransferSequenceCommand(lowerSlideCommands, upperSlideCommands).toAction();
    }

    private Action pickupSequence() {
        return new LowerSlideGrabSequenceCommand(lowSlide).toAction();
    }

    private Action adjustSequence() {
        return new SequentialAction(
                new CameraUpdateDetectorResult(camera).toAction(),
                new DistanceAdjustLUTThetaR(lowSlide, drive,
                        camera::getTx, camera::getTy, camera::getPx, camera::getPy,
                        () -> {
                        }, () -> {
                })
                        .toAction());
    }

    @Override
    public void runOpMode() throws InterruptedException {
        // Initialize subsystems
        lowSlide = new LowerSlide();
        upSlide = new UpperSlide();
        lowSlide.initialize(hardwareMap);
        upSlide.initialize(hardwareMap);

        // Initialize command factories
        lowerSlideCommands = new LowerSlideCommands(lowSlide);
        upperSlideCommands = new UpperSlideCommands(upSlide);

        // cam
        camera = new Limelight();
        camera.initialize(hardwareMap);
        camera.cameraStart();
        LimeLightImageTools llIt = new LimeLightImageTools(camera.limelight);
        llIt.setDriverStationStreamSource();
        llIt.forwardAll();
        FtcDashboard.getInstance().startCameraStream(llIt.getStreamSource(), 10);

        // Initialize drive with starting pose
        drive = new MecanumDrive(hardwareMap, START.pose);

        // Start position
        Actions.runBlocking(
                new SequentialAction(
                        lowerSlideCommands.up(),
                        upperSlideCommands.scorespec(),
                        upperSlideCommands.closeClaw()));

        waitForStart();

        if (isStopRequested())
            return;

        // Full autonomous sequence
        Actions.runBlocking(
                new ParallelAction(
                        new LowerSlideUpdatePID(lowSlide).toAction(),
                        new UpperSlideUpdatePID(upSlide).toAction(),
                        // Add camera telemetry for debugging
                        packet -> {
                            camera.updateTelemetry(packet);
                            return true; // Always continue running
                        },
                        new SequentialAction(
                                upperSlideCommands.scorespec(),
                                scoreSequence(START,
                                        ConfigVariables.AutoTesting.Z_LowerslideExtend_FIRST),
                                new ParallelAction(
                                        pickupAndScoreSequence(SCORE, PICKUP1,
                                                ConfigVariables.AutoTesting.Z_LowerslideExtend_FIRST
                                        ),
                                        lowerSlideCommands.setSpinClawDeg(
                                                ConfigVariables.LowerSlideVars.ZERO
                                                        + 45)),
                                new ParallelAction(
                                        pickupAndScoreSequence(SCORE, PICKUP2,
                                                ConfigVariables.AutoTesting.Z_LowerslideExtend_SECOND
                                        ),
                                        lowerSlideCommands.setSpinClawDeg(
                                                ConfigVariables.LowerSlideVars.ZERO
                                                        + 45)),
                                new ParallelAction(
                                        pickupAndScoreSequence(SCORE, PICKUP3,
                                                ConfigVariables.AutoTesting.Z_LowerslideExtend_THIRD
                                        ),
                                        lowerSlideCommands.setSpinClawDeg(
                                                ConfigVariables.LowerSlideVars.ZERO
                                                        + 90)),
                                tankPickupAndScoreSequence()
                        )
                ));
        RobotStateStore.save(drive.localizer.getPose(), lowSlide.getCurrentPosition(), upSlide.getCurrentPosition());
    }
}
