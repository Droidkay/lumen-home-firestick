package com.droidkay.lumen;

import java.util.Calendar;

/** Verse of the day (King James Version, public domain). Changes once a day. */
final class Verses {
    private Verses() {}

    static final String[][] ALL = {
        {"The LORD is my shepherd; I shall not want.", "Psalm 23:1"},
        {"God is our refuge and strength, a very present help in trouble.", "Psalm 46:1"},
        {"I can do all things through Christ which strengtheneth me.", "Philippians 4:13"},
        {"Fear thou not; for I am with thee: be not dismayed; for I am thy God: I will strengthen thee; yea, I will help thee.", "Isaiah 41:10"},
        {"For I know the thoughts that I think toward you, saith the LORD, thoughts of peace, and not of evil, to give you an expected end.", "Jeremiah 29:11"},
        {"Trust in the LORD with all thine heart; and lean not unto thine own understanding.", "Proverbs 3:5"},
        {"In all thy ways acknowledge him, and he shall direct thy paths.", "Proverbs 3:6"},
        {"Be strong and of a good courage; be not afraid, neither be thou dismayed: for the LORD thy God is with thee whithersoever thou goest.", "Joshua 1:9"},
        {"And we know that all things work together for good to them that love God, to them who are the called according to his purpose.", "Romans 8:28"},
        {"Come unto me, all ye that labour and are heavy laden, and I will give you rest.", "Matthew 11:28"},
        {"But they that wait upon the LORD shall renew their strength; they shall mount up with wings as eagles.", "Isaiah 40:31"},
        {"He that dwelleth in the secret place of the most High shall abide under the shadow of the Almighty.", "Psalm 91:1"},
        {"This is the day which the LORD hath made; we will rejoice and be glad in it.", "Psalm 118:24"},
        {"His compassions fail not. They are new every morning: great is thy faithfulness.", "Lamentations 3:22-23"},
        {"Peace I leave with you, my peace I give unto you. Let not your heart be troubled, neither let it be afraid.", "John 14:27"},
        {"For God hath not given us the spirit of fear; but of power, and of love, and of a sound mind.", "2 Timothy 1:7"},
        {"My help cometh from the LORD, which made heaven and earth.", "Psalm 121:2"},
        {"But seek ye first the kingdom of God, and his righteousness; and all these things shall be added unto you.", "Matthew 6:33"},
        {"The LORD is my light and my salvation; whom shall I fear?", "Psalm 27:1"},
        {"No weapon that is formed against thee shall prosper.", "Isaiah 54:17"},
        {"He will not fail thee, nor forsake thee.", "Deuteronomy 31:6"},
        {"Delight thyself also in the LORD; and he shall give thee the desires of thine heart.", "Psalm 37:4"},
        {"In every thing by prayer and supplication with thanksgiving let your requests be made known unto God.", "Philippians 4:6"},
        {"But my God shall supply all your need according to his riches in glory by Christ Jesus.", "Philippians 4:19"},
        {"Now faith is the substance of things hoped for, the evidence of things not seen.", "Hebrews 11:1"},
        {"O taste and see that the LORD is good: blessed is the man that trusteth in him.", "Psalm 34:8"},
        {"The LORD bless thee, and keep thee: The LORD make his face shine upon thee, and be gracious unto thee.", "Numbers 6:24-25"},
        {"Thy word is a lamp unto my feet, and a light unto my path.", "Psalm 119:105"},
        {"Now the God of hope fill you with all joy and peace in believing.", "Romans 15:13"},
        {"Thou wilt keep him in perfect peace, whose mind is stayed on thee: because he trusteth in thee.", "Isaiah 26:3"},
        {"If any of you lack wisdom, let him ask of God, that giveth to all men liberally.", "James 1:5"},
        {"Casting all your care upon him; for he careth for you.", "1 Peter 5:7"},
        {"Be still, and know that I am God.", "Psalm 46:10"},
        {"In the world ye shall have tribulation: but be of good cheer; I have overcome the world.", "John 16:33"},
        {"What things soever ye desire, when ye pray, believe that ye receive them, and ye shall have them.", "Mark 11:24"},
        {"Weeping may endure for a night, but joy cometh in the morning.", "Psalm 30:5"},
        {"The LORD thy God in the midst of thee is mighty; he will save, he will rejoice over thee with joy.", "Zephaniah 3:17"},
        {"And let us not be weary in well doing: for in due season we shall reap, if we faint not.", "Galatians 6:9"},
        {"In thy presence is fulness of joy; at thy right hand there are pleasures for evermore.", "Psalm 16:11"},
        {"For we walk by faith, not by sight.", "2 Corinthians 5:7"},
    };

    static String[] today() {
        Calendar c = Calendar.getInstance();
        int idx = (c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR)) % ALL.length;
        return ALL[idx];
    }
}
