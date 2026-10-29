package com.harrylong.tvchess.learn

import com.harrylong.tvchess.model.PieceType
import com.harrylong.tvchess.model.Position

/**
 * Kho dữ liệu 12 bài học cờ vua chuẩn sư phạm dành cho trẻ em Việt Nam ("Bé Đậu").
 */
object LessonRepository {

    val lessons: List<Lesson> = listOf(
        // =====================================================================
        // BÀI 1: LÀM QUEN BÀN CỜ
        // =====================================================================
        Lesson(
            id = "board_intro",
            orderNumber = 1,
            title = "Làm quen bàn cờ",
            subtitle = "64 ô vuông, hàng, cột & trung tâm",
            emoji = "🏁",
            steps = listOf(
                ExplainStep(
                    title = "Bàn cờ 64 ô xinh xắn",
                    kidText = "Chào bé Đậu dễ thương! Bàn cờ vua có 64 ô vuông xinh xắn xen kẽ sáng và tối nè. Bốn ô ở giữa gọi là trung tâm nha!",
                    voiceKey = "voice_lesson_board_intro_1",
                    fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    highlightedSquares = listOf(Position(3, 3), Position(3, 4), Position(4, 3), Position(4, 4))
                ),
                ExplainStep(
                    title = "Hàng ngang và Cột dọc",
                    kidText = "Đậu nhìn nè, các ô nằm ngang gọi là Hàng, còn các ô chạy dọc từ trên xuống gọi là Cột nghen Đậu!",
                    voiceKey = "voice_lesson_board_intro_2",
                    fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    highlightedSquares = (0..7).map { Position(4, it) },
                    arrows = listOf(Position(7, 4) to Position(0, 4))
                ),
                ExplainStep(
                    title = "Đội hình xuất phát",
                    kidText = "Đây là lúc hai đội xếp quân nè! Đội Trắng ở dưới, đội Đen ở trên, sẵn sàng cùng Đậu thi tài thiệt vui nha!",
                    voiceKey = "voice_lesson_board_intro_3",
                    fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                    arrows = listOf(
                        Position(6, 4) to Position(4, 4),
                        Position(6, 3) to Position(4, 3)
                    )
                ),
                QuizStep(
                    title = "Tìm 4 ô trung tâm",
                    kidText = "Đậu chạm vào 4 ô trung tâm nằm ngay chính giữa bàn cờ giúp mình nha!",
                    voiceKey = "voice_lesson_board_intro_4",
                    fen = "4k3/8/8/8/8/8/8/4K3 w - - 0 1",
                    piecePos = Position(4, 4),
                    correctSquares = setOf(
                        Position(3, 3), Position(3, 4),
                        Position(4, 3), Position(4, 4)
                    ),
                    hint = "4 ô trung tâm là d4, d5, e4, e5 nằm giữa bàn cờ đó Đậu!"
                )
            )
        ),

        // =====================================================================
        // BÀI 2: QUÂN TỐT DŨNG CẢM
        // =====================================================================
        Lesson(
            id = "pawn",
            orderNumber = 2,
            title = "Quân Tốt dũng cảm",
            subtitle = "Đi thẳng, ăn chéo & bước 2 ô đầu tiên",
            emoji = "♟️",
            steps = listOf(
                ExplainStep(
                    title = "Tốt tiến về phía trước",
                    kidText = "Lính Tốt luôn tiến thẳng về trước nha Đậu! Ở nước đầu tiên, Tốt có thể bước 1 ô hoặc nhảy liền 2 ô luôn đó!",
                    voiceKey = "voice_lesson_pawn_1",
                    fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1",
                    arrows = listOf(
                        Position(6, 4) to Position(5, 4),
                        Position(6, 4) to Position(4, 4)
                    )
                ),
                ExplainStep(
                    title = "Tốt ăn chéo 1 ô",
                    kidText = "Lính Tốt đi thẳng nhưng lại ăn chéo 1 ô ở phía trước nghen Đậu! Thấy quân địch ở góc chéo là bắt liền nè!",
                    voiceKey = "voice_lesson_pawn_2",
                    fen = "4k3/8/8/8/3p1p2/4P3/8/4K3 w - - 0 1",
                    arrows = listOf(
                        Position(5, 4) to Position(4, 3),
                        Position(5, 4) to Position(4, 5)
                    )
                ),
                PracticeStep(
                    title = "Bé đi Tốt 2 ô mở màn",
                    kidText = "Đậu ơi, hãy giúp chú lính Tốt nhảy 2 ô tiến lên e4 thiệt dũng cảm nha!",
                    voiceKey = "voice_lesson_pawn_3",
                    fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(6, 4), to = Position(4, 4)),
                    recommendedFrom = Position(6, 4),
                    recommendedTo = Position(4, 4),
                    hint = "Đậu chạm vào chú Tốt ở ô e2 rồi bấm vào ô e4 nha!"
                ),
                PracticeStep(
                    title = "Tốt ăn chéo quân địch",
                    kidText = "Có quân địch ở ô d4 kìa! Đậu cho Tốt ăn chéo để tiêu diệt đối phương nghen!",
                    voiceKey = "voice_lesson_pawn_4",
                    fen = "4k3/8/8/8/3p4/4P3/8/4K3 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(5, 4), to = Position(4, 3)),
                    recommendedFrom = Position(5, 4),
                    recommendedTo = Position(4, 3),
                    hint = "Đậu chạm vào Tốt e3 rồi bấm vào ô d4 để ăn quân địch nghen!"
                ),
                PracticeStep(
                    title = "Ăn hết sao cùng Tốt",
                    kidText = "Bé Đậu chọn một ô chéo để Lính Tốt ăn điểm ngôi sao nha!",
                    voiceKey = "voice_lesson_pawn_5",
                    fen = "4k3/8/8/2p1p3/3P4/8/8/4K3 w - - 0 1",
                    goal = PracticeGoal.MovePieceToTarget(
                        from = Position(4, 3),
                        targetSquares = setOf(Position(3, 2), Position(3, 4))
                    ),
                    targetSquares = setOf(Position(3, 2), Position(3, 4)),
                    recommendedFrom = Position(4, 3),
                    recommendedTo = Position(3, 2),
                    hint = "Tốt ở d4 có thể ăn sang ô c5 hoặc ô e5 đó Đậu!"
                )
            )
        ),

        // =====================================================================
        // BÀI 3: QUÂN XE DŨNG MÃNH
        // =====================================================================
        Lesson(
            id = "rook",
            orderNumber = 3,
            title = "Quân Xe dũng mãnh",
            subtitle = "Chạy thẳng ngang dọc không giới hạn",
            emoji = "🏰",
            steps = listOf(
                ExplainStep(
                    title = "Xe chạy ngang và dọc",
                    kidText = "Xe Pháo Đài chạy ngang và dọc bao xa cũng được hết trơn, miễn là không bị ai cản đường nha Đậu!",
                    voiceKey = "voice_lesson_rook_1",
                    fen = "4k3/8/8/8/4R3/8/8/4K3 w - - 0 1",
                    arrows = listOf(
                        Position(4, 4) to Position(0, 4),
                        Position(4, 4) to Position(7, 4),
                        Position(4, 4) to Position(4, 0),
                        Position(4, 4) to Position(4, 7)
                    )
                ),
                ExplainStep(
                    title = "Xe bị chặn đường",
                    kidText = "Xe không thể nhảy qua đầu quân khác được nha Đậu. Gặp quân địch thì Xe ăn luôn, còn quân mình thì phải dừng lại nè!",
                    voiceKey = "voice_lesson_rook_2",
                    fen = "4k3/8/8/4p3/1P2R2p/8/8/4K3 w - - 0 1",
                    arrows = listOf(
                        Position(4, 4) to Position(4, 7),
                        Position(4, 4) to Position(3, 4)
                    )
                ),
                PracticeStep(
                    title = "Lái Xe phi nước đại",
                    kidText = "Đậu lái chú Xe phi thẳng một mạch từ ô a1 lên tận a8 cho mình xem nào!",
                    voiceKey = "voice_lesson_rook_3",
                    fen = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(7, 0), to = Position(0, 0)),
                    recommendedFrom = Position(7, 0),
                    recommendedTo = Position(0, 0),
                    hint = "Đậu bấm vào Xe ở a1 rồi chạm vào ô a8 ở đầu bên kia bàn cờ nghen!"
                ),
                PracticeStep(
                    title = "Xe ăn quân cản đường",
                    kidText = "Có một chú lính đứng cản đường ở e4! Đậu lái Xe chạy thẳng lên bắt quân nghen!",
                    voiceKey = "voice_lesson_rook_4",
                    fen = "4k3/8/8/8/4p3/8/8/4R1K1 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(7, 4), to = Position(4, 4)),
                    recommendedFrom = Position(7, 4),
                    recommendedTo = Position(4, 4),
                    hint = "Đậu bấm vào Xe ở e1 rồi chạm vào ô e4 để ăn quân đen nha!"
                ),
                PracticeStep(
                    title = "Xe săn ngôi sao",
                    kidText = "Bé Đậu điều khiển Xe chạy thẳng lên ô c5 ăn ngôi sao lấp lánh nghen!",
                    voiceKey = "voice_lesson_rook_5",
                    fen = "4k3/8/8/2p5/8/8/8/2R1K3 w - - 0 1",
                    goal = PracticeGoal.MovePieceToTarget(
                        from = Position(7, 2),
                        targetSquares = setOf(Position(3, 2))
                    ),
                    targetSquares = setOf(Position(3, 2)),
                    recommendedFrom = Position(7, 2),
                    recommendedTo = Position(3, 2),
                    hint = "Đậu chọn Xe c1 rồi phi thẳng lên ô c5 ăn sao nè!"
                )
            )
        ),

        // =====================================================================
        // BÀI 4: QUÂN TƯỢNG NHANH NHẸN
        // =====================================================================
        Lesson(
            id = "bishop",
            orderNumber = 4,
            title = "Quân Tượng nhanh nhẹn",
            subtitle = "Lướt theo đường chéo cùng màu",
            emoji = "⛪",
            steps = listOf(
                ExplainStep(
                    title = "Tượng lướt đường chéo",
                    kidText = "Quân Tượng lướt theo các đường chéo siêu nhanh luôn! Tượng đứng ô màu nào thì suốt đời chỉ đi ô màu đó thôi nha Đậu!",
                    voiceKey = "voice_lesson_bishop_1",
                    fen = "4k3/8/8/8/4B3/8/8/4K3 w - - 0 1",
                    arrows = listOf(
                        Position(4, 4) to Position(1, 1),
                        Position(4, 4) to Position(1, 7),
                        Position(4, 4) to Position(7, 1),
                        Position(4, 4) to Position(7, 7)
                    )
                ),
                QuizStep(
                    title = "Tượng ngắm ô chéo",
                    kidText = "Chú Tượng ở ô d4 đang ngắm các ô chéo nè! Đậu chạm vào các ô đường chéo mà Tượng có thể tới nghen!",
                    voiceKey = "voice_lesson_bishop_2",
                    fen = "4k3/8/8/8/3B4/8/8/4K3 w - - 0 1",
                    piecePos = Position(4, 3),
                    correctSquares = setOf(
                        Position(3, 2), Position(2, 1), Position(1, 0),
                        Position(5, 4), Position(6, 5), Position(7, 6),
                        Position(3, 4), Position(2, 5), Position(1, 6), Position(0, 7),
                        Position(5, 2), Position(6, 1), Position(7, 0)
                    ),
                    hint = "Tượng d4 đi theo 4 hướng chéo ô màu sẫm đó Đậu!"
                ),
                PracticeStep(
                    title = "Tượng lướt chéo bắt quân",
                    kidText = "Mục tiêu ở tận ô b7 kìa Đậu ơi! Đậu cho Tượng lướt một đường chéo thật dài tới bắt quân nha!",
                    voiceKey = "voice_lesson_bishop_3",
                    fen = "4k3/8/1p6/8/8/8/8/4K2B w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(7, 7), to = Position(1, 1)),
                    recommendedFrom = Position(7, 7),
                    recommendedTo = Position(1, 1),
                    hint = "Đậu chọn Tượng ở h1 rồi chạm vào ô b7 ở góc trên bên trái nha!"
                )
            )
        ),

        // =====================================================================
        // BÀI 5: QUÂN MÃ THÔNG MINH
        // =====================================================================
        Lesson(
            id = "knight",
            orderNumber = 5,
            title = "Quân Mã thông minh",
            subtitle = "Bước chữ L & biệt tài nhảy qua đầu quân",
            emoji = "🐴",
            steps = listOf(
                ExplainStep(
                    title = "Mã nhảy hình chữ L",
                    kidText = "Ngựa Chiến đi theo hình chữ L độc đáo nè: bước 2 ô thẳng rồi quẹo 1 ô! Nhảy chữ L thiệt là vui nha Đậu!",
                    voiceKey = "voice_lesson_knight_1",
                    fen = "4k3/8/8/8/4N3/8/8/4K3 w - - 0 1",
                    highlightedSquares = listOf(
                        Position(2, 3), Position(2, 5), Position(3, 2), Position(3, 6),
                        Position(5, 2), Position(5, 6), Position(6, 3), Position(6, 5)
                    )
                ),
                ExplainStep(
                    title = "Mã nhảy qua đầu quân khác",
                    kidText = "Đặc biệt nhất trần đời: chỉ có duy nhất Ngựa Chiến là nhảy qua đầu các quân khác được thôi đó Đậu, không sợ bị cản đường!",
                    voiceKey = "voice_lesson_knight_2",
                    fen = "4k3/8/8/8/8/3PPP2/3PNP2/4K3 w - - 0 1",
                    arrows = listOf(
                        Position(6, 4) to Position(4, 3),
                        Position(6, 4) to Position(4, 5)
                    )
                ),
                PracticeStep(
                    title = "Nhảy Mã qua hàng rào",
                    kidText = "Phía trước có hàng rào lính, Đậu hãy điều khiển chú Ngựa nhảy qua đầu lên ô f3 nha!",
                    voiceKey = "voice_lesson_knight_3",
                    fen = "4k3/8/8/8/8/8/3PPP2/4K1N1 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(7, 6), to = Position(5, 5)),
                    recommendedFrom = Position(7, 6),
                    recommendedTo = Position(5, 5),
                    hint = "Đậu bấm Ngựa ở g1 rồi nhảy chữ L lên ô f3 nghen!"
                ),
                PracticeStep(
                    title = "Mã săn mục tiêu chữ L",
                    kidText = "Mục tiêu ở ô e5 kìa! Đậu cho Ngựa nhảy chữ L tới ăn gọn đối phương nghen!",
                    voiceKey = "voice_lesson_knight_4",
                    fen = "4k3/8/8/4p3/8/5N2/8/4K3 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(5, 5), to = Position(3, 4)),
                    recommendedFrom = Position(5, 5),
                    recommendedTo = Position(3, 4),
                    hint = "Đậu chạm vào Ngựa f3 rồi nhảy lên ô e5 để ăn quân Tốt đen nha!"
                )
            )
        ),

        // =====================================================================
        // BÀI 6: QUÂN HẬU QUYỀN NĂNG
        // =====================================================================
        Lesson(
            id = "queen",
            orderNumber = 6,
            title = "Quân Hậu quyền năng",
            subtitle = "Sức mạnh vô song kết hợp cả Xe và Tượng",
            emoji = "👸",
            steps = listOf(
                ExplainStep(
                    title = "Hậu là siêu anh hùng",
                    kidText = "Nữ Hoàng Hậu là quân mạnh nhất bàn cờ nè Đậu! Hậu vừa đi thẳng như Xe, vừa đi chéo như Tượng luôn, siêu cấp lợi hại!",
                    voiceKey = "voice_lesson_queen_1",
                    fen = "4k3/8/8/8/4Q3/8/8/4K3 w - - 0 1",
                    arrows = listOf(
                        Position(4, 4) to Position(0, 4),
                        Position(4, 4) to Position(4, 0),
                        Position(4, 4) to Position(1, 1),
                        Position(4, 4) to Position(7, 7)
                    )
                ),
                PracticeStep(
                    title = "Hậu đi đường thẳng bắt quân",
                    kidText = "Đậu cho Nữ Hoàng đi thẳng lên một bước để bắt chú lính đen ở e2 nha!",
                    voiceKey = "voice_lesson_queen_2",
                    fen = "4k3/8/8/8/8/8/4p3/4Q1K1 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(7, 4), to = Position(6, 4)),
                    recommendedFrom = Position(7, 4),
                    recommendedTo = Position(6, 4),
                    hint = "Đậu bấm vào Hậu ở e1 rồi tiến lên ô e2 ăn quân nghen!"
                ),
                PracticeStep(
                    title = "Hậu lướt đường chéo bắt quân",
                    kidText = "Giờ Nữ Hoàng lướt chéo sang ô b4 để bắt quân nè! Đậu ra tay giúp Nữ Hoàng nghen!",
                    voiceKey = "voice_lesson_queen_3",
                    fen = "4k3/8/8/8/1p6/8/8/4Q1K1 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(7, 4), to = Position(4, 1)),
                    recommendedFrom = Position(7, 4),
                    recommendedTo = Position(4, 1),
                    hint = "Đậu bấm vào Hậu e1 rồi lướt chéo lên ô b4 nha!"
                )
            )
        ),

        // =====================================================================
        // BÀI 7: QUÂN VUA QUAN TRỌNG
        // =====================================================================
        Lesson(
            id = "king",
            orderNumber = 7,
            title = "Quân Vua quan trọng",
            subtitle = "Trái tim ván cờ, mỗi bước đi 1 ô",
            emoji = "👑",
            steps = listOf(
                ExplainStep(
                    title = "Đức Vua bước từng ô",
                    kidText = "Đức Vua là trái tim của cả ván cờ đó Đậu ơi! Vua đi được mọi hướng nhưng chỉ bước từng ô một thôi nha!",
                    voiceKey = "voice_lesson_king_1",
                    fen = "4k3/8/8/8/4K3/8/8/8 w - - 0 1",
                    arrows = listOf(
                        Position(4, 4) to Position(3, 4),
                        Position(4, 4) to Position(5, 4),
                        Position(4, 4) to Position(4, 3),
                        Position(4, 4) to Position(4, 5),
                        Position(4, 4) to Position(3, 3),
                        Position(4, 4) to Position(3, 5)
                    )
                ),
                ExplainStep(
                    title = "Cấm Vua bước vào chỗ nguy hiểm",
                    kidText = "Đức Vua không bao giờ được tự bước vào ô đang bị quân địch ngắm bắn đâu nha Đậu, nguy hiểm lắm đó!",
                    voiceKey = "voice_lesson_king_2",
                    fen = "4k3/8/8/8/4r3/8/8/4K3 w - - 0 1",
                    highlightedSquares = listOf(
                        Position(4, 4), Position(5, 4), Position(6, 4), Position(7, 4)
                    )
                ),
                PracticeStep(
                    title = "Vua bước sang ô an toàn",
                    kidText = "Xe địch đang ngắm Vua ở e1 kìa! Đậu dắt Đức Vua bước sang ô d1 an toàn nha!",
                    voiceKey = "voice_lesson_king_3",
                    fen = "4k3/8/8/8/4r3/8/8/4K3 w - - 0 1",
                    goal = PracticeGoal.EscapeCheck,
                    recommendedFrom = Position(7, 4),
                    recommendedTo = Position(7, 3),
                    hint = "Đậu chạm vào Vua ở e1 rồi dắt sang ô d1 hoặc f1 để tránh đường bắn nghen!"
                )
            )
        ),

        // =====================================================================
        // BÀI 8: NƯỚC ĐI ĐẶC BIỆT
        // =====================================================================
        Lesson(
            id = "special_moves",
            orderNumber = 8,
            title = "Nước đi đặc biệt",
            subtitle = "Nhập thành bảo vệ Vua & Phong cấp biến hình",
            emoji = "✨",
            steps = listOf(
                ExplainStep(
                    title = "Nhập thành bảo vệ Vua",
                    kidText = "Nhập thành là tuyệt chiêu duy nhất mà Vua và Xe đi cùng một lúc! Vua nhảy 2 ô về phía Xe để vào thành an toàn nghen Đậu!",
                    voiceKey = "voice_lesson_special_moves_1",
                    fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1",
                    arrows = listOf(
                        Position(7, 4) to Position(7, 6),
                        Position(7, 7) to Position(7, 5)
                    )
                ),
                PracticeStep(
                    title = "Thực hiện Nhập thành cánh Vua",
                    kidText = "Đậu chạm vào Đức Vua ở e1 rồi dắt Vua sang ô g1 để thực hiện Nhập Thành nghen!",
                    voiceKey = "voice_lesson_special_moves_2",
                    fen = "4k3/8/8/8/8/8/8/4K2R w K - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(7, 4), to = Position(7, 6)),
                    recommendedFrom = Position(7, 4),
                    recommendedTo = Position(7, 6),
                    hint = "Đậu chọn Vua e1 rồi bấm vào ô g1, chú Xe h1 sẽ tự động bay vào ô f1 che chở đó!"
                ),
                ExplainStep(
                    title = "Phong cấp - Tốt biến thành Hậu",
                    kidText = "Kỳ diệu chưa nè! Khi chú lính Tốt dũng cảm tiến tới hàng cuối cùng, Tốt sẽ được biến hình thành Nữ Hoàng quyền năng luôn!",
                    voiceKey = "voice_lesson_special_moves_3",
                    fen = "7k/4P3/8/8/8/8/8/4K3 w - - 0 1",
                    arrows = listOf(Position(1, 4) to Position(0, 4))
                ),
                PracticeStep(
                    title = "Phong cấp thành Hậu",
                    kidText = "Đậu cho Lính Tốt bước lên ô e8 và biến hình thành Nữ Hoàng rực rỡ nha!",
                    voiceKey = "voice_lesson_special_moves_4",
                    fen = "7k/4P3/8/8/8/8/8/4K3 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(
                        from = Position(1, 4),
                        to = Position(0, 4),
                        promotionPiece = PieceType.QUEEN
                    ),
                    recommendedFrom = Position(1, 4),
                    recommendedTo = Position(0, 4),
                    hint = "Đậu chạm vào Tốt ở e7, đẩy lên ô e8 rồi chọn vương miện Nữ Hoàng nghen!"
                )
            )
        ),

        // =====================================================================
        // BÀI 9: CHIẾU TƯỚNG!
        // =====================================================================
        Lesson(
            id = "check",
            orderNumber = 9,
            title = "Chiếu tướng!",
            subtitle = "3 cách thoát hiểm thần kỳ: Chạy - Chặn - Ăn",
            emoji = "⚡",
            steps = listOf(
                ExplainStep(
                    title = "Chiếu tướng là gì?",
                    kidText = "Khi quân địch tấn công thẳng vào Đức Vua, đó gọi là Chiếu Tướng! Lúc này Đậu phải giải cứu Đức Vua ngay nghen!",
                    voiceKey = "voice_lesson_check_1",
                    fen = "4k3/8/8/8/8/8/8/4K2r w - - 0 1",
                    arrows = listOf(Position(7, 7) to Position(7, 4)),
                    highlightedSquares = listOf(Position(7, 4))
                ),
                ExplainStep(
                    title = "Ba cách thoát chiếu: Chạy - Chặn - Ăn",
                    kidText = "Nhớ câu thần chú này nè Đậu ơi: 1 là Chạy Vua, 2 là Chặn đường, 3 là Ăn luôn quân chiếu! Dễ nhớ thiệt ha!",
                    voiceKey = "voice_lesson_check_2",
                    fen = "4k3/8/8/8/8/8/8/4K2r w - - 0 1"
                ),
                PracticeStep(
                    title = "Cách 1: Chạy Vua",
                    kidText = "Vua đang bị chiếu ở e1! Đậu hãy dắt Vua Chạy lên ô d2 an toàn nha!",
                    voiceKey = "voice_lesson_check_3",
                    fen = "4k3/8/8/8/8/8/8/4K2r w - - 0 1",
                    goal = PracticeGoal.EscapeCheck,
                    recommendedFrom = Position(7, 4),
                    recommendedTo = Position(6, 3),
                    hint = "Đậu chạm vào Vua e1 rồi chạy lên ô d2 hoặc f2 để thoát chiếu nghen!"
                ),
                PracticeStep(
                    title = "Cách 2: Chặn đường chiếu",
                    kidText = "Cách thứ 2 nè: Đậu lấy Tượng ở f3 Chặn đường chiếu ở ô e2 để che chở cho Vua nha!",
                    voiceKey = "voice_lesson_check_4",
                    fen = "4r1k1/8/8/8/8/5B2/8/4K3 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(5, 5), to = Position(6, 4)),
                    recommendedFrom = Position(5, 5),
                    recommendedTo = Position(6, 4),
                    hint = "Đậu bấm vào Tượng f3 rồi trượt xuống ô e2 đứng chắn trước mặt Vua nha!"
                ),
                PracticeStep(
                    title = "Cách 3: Ăn luôn quân chiếu",
                    kidText = "Cách thứ 3 siêu ngầu: Đậu cho Ngựa ở g3 Ăn luôn con Xe đang chiếu ở h1 nha!",
                    voiceKey = "voice_lesson_check_5",
                    fen = "4k3/8/8/8/8/6N1/8/4K2r w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(5, 6), to = Position(7, 7)),
                    recommendedFrom = Position(5, 6),
                    recommendedTo = Position(7, 7),
                    hint = "Đậu chọn Ngựa g3 rồi nhảy chữ L tới h1 tiêu diệt luôn con Xe đối phương nghen!"
                )
            )
        ),

        // =====================================================================
        // BÀI 10: CÁCH THẮNG VÁN CỜ
        // =====================================================================
        Lesson(
            id = "checkmate",
            orderNumber = 10,
            title = "Cách thắng ván cờ",
            subtitle = "Chiếu hết bất bại & giải câu đố 1 nước",
            emoji = "🏆",
            steps = listOf(
                ExplainStep(
                    title = "Chiếu hết là chiến thắng!",
                    kidText = "Khi Đức Vua bị chiếu mà không còn đường Chạy, không có ai Chặn và không thể Ăn được, đó là Chiếu Hết! Ta giành chiến thắng rực rỡ nha Đậu!",
                    voiceKey = "voice_lesson_checkmate_1",
                    fen = "R3k3/8/4K3/8/8/8/8/8 b - - 0 1",
                    highlightedSquares = listOf(Position(0, 4))
                ),
                PracticeStep(
                    title = "Chiếu hết 1 nước - Hậu kết liễu",
                    kidText = "Đậu cho Nữ Hoàng ở f3 bay lên ô f7 ăn quân chiếu hết để giành cúp vô địch nha!",
                    voiceKey = "voice_lesson_checkmate_2",
                    fen = "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(5, 5), to = Position(1, 5)),
                    recommendedFrom = Position(5, 5),
                    recommendedTo = Position(1, 5),
                    hint = "Đậu chọn Hậu ở f3 rồi bay thẳng lên ô f7 ăn chú lính đen nha!"
                ),
                PracticeStep(
                    title = "Chiếu hết 1 nước - Xe đáy bàn",
                    kidText = "Đậu lái chú Xe ở a1 phi thẳng lên a8 chiếu hết Đức Vua đối phương nghen!",
                    voiceKey = "voice_lesson_checkmate_3",
                    fen = "6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1",
                    goal = PracticeGoal.SpecificMove(from = Position(7, 0), to = Position(0, 0)),
                    recommendedFrom = Position(7, 0),
                    recommendedTo = Position(0, 0),
                    hint = "Vua đen bị kẹt sau hàng rào lính, Đậu lái Xe từ a1 phi thẳng lên a8 là thắng liền!"
                )
            )
        ),

        // =====================================================================
        // BÀI 11: HOÀ CỜ
        // =====================================================================
        Lesson(
            id = "draw",
            orderNumber = 11,
            title = "Hoà cờ",
            subtitle = "Hết nước đi (Stalemate) & Không đủ quân",
            emoji = "🤝",
            steps = listOf(
                ExplainStep(
                    title = "Hết nước đi hợp lệ (Stalemate)",
                    kidText = "Nếu Đức Vua không bị chiếu nhưng cả đội không còn bất kỳ nước đi nào hợp lệ, ván cờ sẽ Hoà nha Đậu! Hai bên bắt tay nhau nè!",
                    voiceKey = "voice_lesson_draw_1",
                    fen = "k7/8/1Q6/8/8/8/8/4K3 b - - 0 1",
                    highlightedSquares = listOf(Position(0, 0))
                ),
                ExplainStep(
                    title = "Không đủ quân để chiếu hết",
                    kidText = "Khi trên bàn chỉ còn 2 Đức Vua hoặc không đủ quân để chiếu hết, trọng tài cũng tuyên bố Hoà cờ luôn đó Đậu!",
                    voiceKey = "voice_lesson_draw_2",
                    fen = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
                ),
                PracticeStep(
                    title = "Ván cờ hoà vui vẻ",
                    kidText = "Chỉ còn 2 Đức Vua thôi nè! Đậu bước Vua lên một bước để hoàn thành ván cờ hoà vui vẻ nha!",
                    voiceKey = "voice_lesson_draw_3",
                    fen = "4k3/8/8/8/8/8/8/4K3 w - - 0 1",
                    goal = PracticeGoal.MovePieceToTarget(
                        from = Position(7, 4),
                        targetSquares = setOf(
                            Position(6, 3), Position(6, 4), Position(6, 5),
                            Position(7, 3), Position(7, 5)
                        )
                    ),
                    recommendedFrom = Position(7, 4),
                    recommendedTo = Position(6, 4),
                    hint = "Đậu bước Vua e1 lên ô e2 hay bất kỳ ô lân cận nào cũng được nghen!"
                )
            )
        ),

        // =====================================================================
        // BÀI 12: TỐT NGHIỆP KIỆN TƯỚNG
        // =====================================================================
        Lesson(
            id = "graduation",
            orderNumber = 12,
            title = "Tốt nghiệp Kiện Tướng",
            subtitle = "Bé Đậu sẵn sàng thách đấu Khủng Long Nhí!",
            emoji = "🎓",
            steps = listOf(
                GraduationStep(
                    title = "Chúc mừng Kiện Tướng Nhí Bé Đậu!",
                    kidText = "Hoan hô bé Đậu xuất sắc quá! Đậu đã học hết 12 bài học cờ vua và trở thành Kiện Tướng Nhí rồi nè! Giờ mình cùng thử sức đấu một ván thật với bạn Khủng Long Nhí nha!",
                    voiceKey = "voice_lesson_graduation_1",
                    celebrationText = "Bé Đậu đã chính thức tốt nghiệp Khóa học Cờ Vua Hoàng Gia! Giờ là lúc Đậu tự tin thi đấu cùng các bạn quái thú đáng yêu!"
                )
            )
        )
    )

    fun getLessonById(id: String): Lesson? = lessons.find { it.id == id }

    fun getNextLesson(currentId: String): Lesson? {
        val idx = lessons.indexOfFirst { it.id == currentId }
        return if (idx in 0 until lessons.lastIndex) lessons[idx + 1] else null
    }
}
