{ Inspect ZIP metadata before Inno's native extractor sees the archive.
  These are single-volume ZIPs. The MOD must have one clear install root. }
type
  TModZip = record
    Encrypted: Boolean;
    Wrapped: Boolean;
    ExpandedBytes: Int64;
  end;

procedure RequireModZip(Condition: Boolean);
begin
  if not Condition then
    RaiseException(CustomMessage('ModInvalid'));
end;

function Zip16(const Bytes: AnsiString; Offset: Integer): Integer;
begin
  Result := Ord(Bytes[Offset + 1]) + Ord(Bytes[Offset + 2]) * 256;
end;

function Zip32(const Bytes: AnsiString; Offset: Integer): Int64;
begin
  Result := Zip16(Bytes, Offset) + Int64(Zip16(Bytes, Offset + 2)) * 65536;
end;

function ReadZipBytes(Stream: TFileStream; Count: Integer): AnsiString;
begin
  SetLength(Result, Count);
  if Count > 0 then
    RequireModZip(Stream.Read(Result, Count) = Count);
end;

function ModEntryPath(const Raw: AnsiString; Flags: Integer): String;
var
  I, Start, Dot: Integer;
  Part, Device: String;
begin
  if (Flags and $800) <> 0 then
    Result := UTF8Decode(Raw)
  else
    Result := String(Raw);
  if Copy(Result, Length(Result), 1) = '/' then
    Delete(Result, Length(Result), 1);
  RequireModZip(Result <> '');
  Start := 1;
  for I := 1 to Length(Result) + 1 do
  begin
    if I <= Length(Result) then
    begin
      RequireModZip((Ord(Result[I]) >= 32) and
        (Pos(Result[I], '\:*?"<>|') = 0));
      if Result[I] <> '/' then
        Continue;
    end;
    Part := Copy(Result, Start, I - Start);
    RequireModZip((Part <> '') and (Part <> '.') and (Part <> '..'));
    RequireModZip((Part[Length(Part)] <> '.') and
      (Part[Length(Part)] <> ' '));
    Dot := Pos('.', Part);
    if Dot <> 0 then
      Part := Copy(Part, 1, Dot - 1);
    Device := Uppercase(Trim(Part));
    RequireModZip((Device <> 'CON') and (Device <> 'PRN') and
      (Device <> 'AUX') and (Device <> 'NUL') and (Device <> 'CONIN$') and
      (Device <> 'CONOUT$') and not ((Length(Device) = 4) and
      (Pos(Copy(Device, 4, 1), '123456789¹²³') <> 0) and
      ((Copy(Device, 1, 3) = 'COM') or (Copy(Device, 1, 3) = 'LPT'))));
    Start := I + 1;
  end;
end;

procedure CheckZipExtra(const Extra: AnsiString; const Name: String);
var
  Offset, Size: Integer;
begin
  Offset := 0;
  while Offset < Length(Extra) do
  begin
    RequireModZip(Offset + 4 <= Length(Extra));
    Size := Zip16(Extra, Offset + 2);
    RequireModZip(Offset + 4 + Size <= Length(Extra));
    { A Unicode path extra field must describe the same Windows path. }
    if Zip16(Extra, Offset) = $7075 then
    begin
      RequireModZip(Size > 5);
      RequireModZip(CompareText(Name,
        ModEntryPath(Copy(Extra, Offset + 10, Size - 5), $800)) = 0);
    end;
    Offset := Offset + 4 + Size;
  end;
end;

function ReadModZip(const FileName: String): TModZip;
var
  Stream: TFileStream;
  Names: TStringList;
  Tail, Entry, Local, RawName: AnsiString;
  Name: String;
  I, Footer, Count, Flags, NameLength: Integer;
  EndOffset, Directory, DirectoryEnd, NextEntry, LocalOffset,
    Attributes: Int64;
  RootManifest, WrappedManifest, AllWrapped: Boolean;
begin
  Result.Encrypted := False;
  Result.ExpandedBytes := 0;
  RootManifest := False;
  WrappedManifest := False;
  AllWrapped := True;
  Stream := TFileStream.Create(FileName, fmOpenRead or fmShareDenyWrite);
  try
    RequireModZip(Stream.Size >= 22);
    EndOffset := Stream.Size - 65557;
    if EndOffset < 0 then
      EndOffset := 0;
    Stream.Position := EndOffset;
    Tail := ReadZipBytes(Stream, Stream.Size - EndOffset);
    Footer := Length(Tail) - 22;
    while Footer >= 0 do
    begin
      if (Zip32(Tail, Footer) = $06054B50) and
        (Zip16(Tail, Footer + 20) = Length(Tail) - Footer - 22) then
        Break;
      Footer := Footer - 1;
    end;
    RequireModZip(Footer >= 0);
    Count := Zip16(Tail, Footer + 10);
    RequireModZip((Zip16(Tail, Footer + 4) = 0) and
      (Zip16(Tail, Footer + 6) = 0) and (Zip16(Tail, Footer + 8) = Count) and
      (Count > 0) and (Count < 65535));
    Directory := Zip32(Tail, Footer + 16);
    DirectoryEnd := Directory + Zip32(Tail, Footer + 12);
    RequireModZip(DirectoryEnd = EndOffset + Footer);
    Stream.Position := Directory;

    Names := TStringList.Create;
    try
      Names.Sorted := True;
      Names.Duplicates := dupError;
      for I := 1 to Count do
      begin
        RequireModZip(Stream.Position + 46 <= DirectoryEnd);
        Entry := ReadZipBytes(Stream, 46);
        NameLength := Zip16(Entry, 28);
        NextEntry := Stream.Position + NameLength + Zip16(Entry, 30) +
          Zip16(Entry, 32);
        RequireModZip((Zip32(Entry, 0) = $02014B50) and
          (Zip16(Entry, 34) = 0) and (NextEntry <= DirectoryEnd));
        RawName := ReadZipBytes(Stream, NameLength);
        Flags := Zip16(Entry, 8);
        Name := ModEntryPath(RawName, Flags);
        CheckZipExtra(ReadZipBytes(Stream, Zip16(Entry, 30)), Name);
        Attributes := Zip32(Entry, 38);
        RequireModZip(((Attributes and $400) = 0) and
          (((Attributes shr 16) and $F000) <> $A000));
        RequireModZip(Names.IndexOf(Name) < 0);
        Names.Add(Name);
        if CompareText(Name, 'mod.xml') = 0 then
          RootManifest := True;
        if CompareText(Name, 'mod/mod.xml') = 0 then
          WrappedManifest := True;
        AllWrapped := AllWrapped and ((CompareText(Name, 'mod') = 0) or
          (CompareText(Copy(Name, 1, 4), 'mod/') = 0));
        RequireModZip((Zip32(Entry, 20) < 4294967295) and
          (Zip32(Entry, 24) < 4294967295));
        Result.ExpandedBytes := Result.ExpandedBytes + Zip32(Entry, 24);
        Result.Encrypted := Result.Encrypted or ((Flags and 1) <> 0);

        LocalOffset := Zip32(Entry, 42);
        RequireModZip(LocalOffset + 30 <= Directory);
        Stream.Position := LocalOffset;
        Local := ReadZipBytes(Stream, 30);
        RequireModZip((Zip32(Local, 0) = $04034B50) and
          (Zip16(Local, 6) = Flags) and (Zip16(Local, 8) = Zip16(Entry, 10)) and
          (Zip16(Local, 26) = NameLength) and
          (Stream.Position + NameLength + Zip16(Local, 28) +
            Zip32(Entry, 20) <= Directory));
        RequireModZip(ReadZipBytes(Stream, NameLength) = RawName);
        CheckZipExtra(ReadZipBytes(Stream, Zip16(Local, 28)), Name);
        Stream.Position := NextEntry;
      end;
      RequireModZip(Stream.Position = DirectoryEnd);
    finally
      Names.Free;
    end;
  finally
    Stream.Free;
  end;
  RequireModZip((RootManifest xor WrappedManifest) and
    (RootManifest or AllWrapped));
  Result.Wrapped := WrappedManifest;
end;
