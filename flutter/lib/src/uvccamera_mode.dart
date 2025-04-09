import 'package:equatable/equatable.dart';
import 'package:flutter/cupertino.dart';

import 'uvccamera_frame_format.dart';

@immutable
class UvcCameraMode extends Equatable {
  final int frameWidth;
  final int frameHeight;
  final UvcCameraFrameFormat frameFormat;
  final int frameIntervalType;
  final int frameIntervalIndex;
  final List<double> fps;

  const UvcCameraMode({required this.frameWidth, required this.frameHeight, required this.frameFormat,
    required this.frameIntervalType, required this.frameIntervalIndex, required this.fps
  });

  /// The aspect ratio of the camera mode.
  double get aspectRatio => frameWidth / frameHeight;

  int get curFps => fps[frameIntervalIndex].toInt();

  factory UvcCameraMode.fromMap(Map<dynamic, dynamic> map) {
    return UvcCameraMode(
      frameWidth: map['frameWidth'] as int,
      frameHeight: map['frameHeight'] as int,
      frameFormat: UvcCameraFrameFormat.values.byName(map['frameFormat'] as String),
      frameIntervalType: map['frameIntervalType'] as int,
      frameIntervalIndex: map['frameIntervalIndex'] as int,
      fps: List<double>.from(map['fps'] as List),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'frameWidth': frameWidth,
      'frameHeight': frameHeight,
      'frameFormat': frameFormat.name,
      'frameIntervalType': frameIntervalType,
      'frameIntervalIndex': frameIntervalIndex,
      'fps': fps
    };
  }

  UvcCameraMode copyWith({
    int? frameWidth,
    int? frameHeight,
    UvcCameraFrameFormat? frameFormat,
    int? frameIntervalType,
    int? frameIntervalIndex,
    List<double>? fps,
  }) {
    return UvcCameraMode(
      frameWidth: frameWidth ?? this.frameWidth,
      frameHeight: frameHeight ?? this.frameHeight,
      frameFormat: frameFormat ?? this.frameFormat,
      frameIntervalType: frameIntervalType ?? this.frameIntervalType,
      frameIntervalIndex: frameIntervalIndex ?? this.frameIntervalIndex,
      fps: fps ?? this.fps,
    );
  }

  @override
  List<Object?> get props => [frameWidth, frameHeight, frameFormat, frameIntervalIndex];
}
