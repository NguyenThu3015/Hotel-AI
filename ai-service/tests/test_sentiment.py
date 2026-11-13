from app.sentiment import analyze


def test_negative_review_extracts_topics():
    result = analyze("Phòng bẩn, rất ồn và nhân viên phục vụ chậm")
    assert result["sentiment"] == "NEGATIVE"
    assert "vệ sinh" in result["topics"]
    assert "tiếng ồn" in result["topics"]
    assert "nhân viên" in result["topics"]
